package de.oejendorferdamm.dammboard.data

import de.oejendorferdamm.dammboard.model.IServEintrag
import de.oejendorferdamm.dammboard.model.IServZugang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import javax.xml.parsers.DocumentBuilderFactory

private const val PROPFIND_KOERPER = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:displayname/></d:prop></d:propfind>"""

/** Obergrenze für eine Ordnerliste – schützt vor riesigen oder endlosen Antworten. */
private const val MAX_ANTWORT_BYTES = 4 * 1024 * 1024

/** Obergrenze für heruntergeladene Arbeitsblätter. */
private const val MAX_DATEI_BYTES = 60 * 1024 * 1024

/**
 * Eigener Client für IServ: folgt keinen Weiterleitungen von https auf http. Sonst könnte eine
 * manipulierte Weiterleitung dafür sorgen, dass Tafelbilder unverschlüsselt übertragen werden.
 * (Zugangsdaten entfernt OkHttp bei Weiterleitungen auf einen anderen Server ohnehin.)
 */
private val iservHttp: OkHttpClient by lazy {
    NetzwerkClient.instance.newBuilder()
        .followSslRedirects(false)
        .build()
}

/** Einfacher WebDAV-Client für den IServ-Dateispeicher: Ordner auflisten und Dateien hochladen. */
class IServClient(private val zugang: IServZugang) {

    /** Basisadresse – nur https, sonst gingen Benutzername und Passwort im Klartext übers Netz. */
    private fun basis(): HttpUrl {
        val url = zugang.serverUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IOException("Die IServ-Adresse ist ungültig. Bitte in den Einstellungen prüfen.")
        if (!url.isHttps) throw IOException("Aus Sicherheitsgründen nur https-Adressen erlaubt.")
        return url
    }

    /**
     * Baut die Adresse für einen Ordner (+ optional Dateinamen). Jeder Pfadteil wird einzeln
     * kodiert – Ordnernamen mit Leerzeichen, "#" oder "?" funktionieren dadurch zuverlässig.
     */
    private fun adresse(pfad: String, dateiname: String? = null): HttpUrl {
        val builder = basis().newBuilder()
        pfad.split('/').filter { it.isNotEmpty() }.forEach { builder.addPathSegment(it) }
        if (dateiname != null) {
            builder.addPathSegment(dateiname)
        } else if (pfad.trim('/').isEmpty()) {
            builder.addPathSegment("") // Hauptordner mit abschließendem "/"
        }
        return builder.build()
    }

    suspend fun liste(pfad: String): Result<List<IServEintrag>> = withContext(Dispatchers.IO) {
        try {
            val angefragterPfad = if (pfad.startsWith("/")) pfad else "/$pfad"
            val anfrage = Request.Builder()
                .url(adresse(angefragterPfad))
                .header("Authorization", Credentials.basic(zugang.benutzername, zugang.passwort))
                .header("Depth", "1")
                .method("PROPFIND", PROPFIND_KOERPER.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            iservHttp.newCall(anfrage).execute().use { antwort ->
                if (!antwort.isSuccessful) {
                    val hinweis = when (antwort.code) {
                        401, 403 -> " – Benutzername oder Passwort stimmen nicht."
                        404 -> " – Ordner nicht gefunden, bitte die Adresse prüfen."
                        else -> ""
                    }
                    return@withContext Result.failure(IOException("IServ antwortete mit HTTP ${antwort.code}$hinweis"))
                }
                val koerper = antwort.body ?: return@withContext Result.failure(IOException("Leere Antwort von IServ"))
                val text = begrenztLesen(koerper)
                val basisPfad = "/" + basis().pathSegments.filter { it.isNotEmpty() }.joinToString("/")
                Result.success(parsePropfind(text, basisPfad.trimEnd('/'), angefragterPfad))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hochladen(pfad: String, dateiname: String, bytes: ByteArray, mime: String = "image/png"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val anfrage = Request.Builder()
                .url(adresse(pfad, dateiname))
                .header("Authorization", Credentials.basic(zugang.benutzername, zugang.passwort))
                .put(bytes.toRequestBody(mime.toMediaType()))
                .build()
            iservHttp.newCall(anfrage).execute().use { antwort ->
                if (antwort.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Hochladen fehlgeschlagen: HTTP ${antwort.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lädt eine Datei herunter (Arbeitsblatt öffnen) – höchstens [MAX_DATEI_BYTES]. */
    suspend fun herunterladen(pfad: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val anfrage = Request.Builder()
                .url(adresse(pfad.substringBeforeLast('/', ""), pfad.substringAfterLast('/')))
                .header("Authorization", Credentials.basic(zugang.benutzername, zugang.passwort))
                .get()
                .build()
            iservHttp.newCall(anfrage).execute().use { antwort ->
                if (!antwort.isSuccessful) return@withContext Result.failure(IOException("Herunterladen fehlgeschlagen: HTTP ${antwort.code}"))
                val koerper = antwort.body ?: return@withContext Result.failure(IOException("Leere Antwort von IServ"))
                if (koerper.contentLength() > MAX_DATEI_BYTES) return@withContext Result.failure(IOException("Die Datei ist zu groß (über 60 MB)"))
                val puffer = ByteArrayOutputStream()
                koerper.byteStream().use { eingabe ->
                    val block = ByteArray(64 * 1024)
                    while (true) {
                        val anzahl = eingabe.read(block)
                        if (anzahl == -1) break
                        puffer.write(block, 0, anzahl)
                        if (puffer.size() > MAX_DATEI_BYTES) return@withContext Result.failure(IOException("Die Datei ist zu groß (über 60 MB)"))
                    }
                }
                Result.success(puffer.toByteArray())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun begrenztLesen(koerper: ResponseBody): String {
        if (koerper.contentLength() > MAX_ANTWORT_BYTES) throw IOException("Antwort von IServ ist zu groß")
        val puffer = ByteArrayOutputStream()
        koerper.byteStream().use { eingabe ->
            val block = ByteArray(8192)
            while (true) {
                val anzahl = eingabe.read(block)
                if (anzahl == -1) break
                puffer.write(block, 0, anzahl)
                if (puffer.size() > MAX_ANTWORT_BYTES) throw IOException("Antwort von IServ ist zu groß")
            }
        }
        return puffer.toString("UTF-8")
    }

    private fun parsePropfind(xml: String, basisPfad: String, angefragterPfad: String): List<IServEintrag> {
        // Eine WebDAV-Antwort braucht keine DOCTYPE-Angaben. Wer welche schickt, bekommt keine
        // Verarbeitung – schützt vor XML-Entity-Tricks (z. B. "Billion Laughs").
        if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
            throw IOException("Unerwartete Antwort von IServ")
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
        }
        val dokument = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val antworten = dokument.getElementsByTagNameNS("DAV:", "response")
        val angefragtNormalisiert = angefragterPfad.trimEnd('/')
        val ergebnis = mutableListOf<IServEintrag>()

        for (i in 0 until antworten.length) {
            val knoten = antworten.item(i) as? Element ?: continue
            val hrefKnoten = knoten.getElementsByTagNameNS("DAV:", "href").item(0) ?: continue
            val hrefRoh = hrefKnoten.textContent?.trim() ?: continue
            // Manche Server liefern volle Adressen statt nur Pfaden.
            val hrefPfad = if (hrefRoh.startsWith("http", ignoreCase = true)) {
                hrefRoh.toHttpUrlOrNull()?.encodedPath ?: continue
            } else {
                hrefRoh
            }
            val href = try {
                // "+" ist in Pfaden ein echtes Pluszeichen (URLDecoder würde ein Leerzeichen daraus machen).
                URLDecoder.decode(hrefPfad.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                hrefPfad
            }
            val istOrdner = knoten.getElementsByTagNameNS("DAV:", "collection").length > 0
            var relativ = href.removePrefix(basisPfad)
            if (!relativ.startsWith("/")) relativ = "/$relativ"
            val normalisiert = relativ.trimEnd('/')
            if (normalisiert == angefragtNormalisiert || normalisiert.isEmpty()) continue
            val name = normalisiert.substringAfterLast('/')
            if (name.isBlank() || name == "." || name == "..") continue
            ergebnis.add(IServEintrag(name = name, pfad = normalisiert, istOrdner = istOrdner))
        }

        return ergebnis.sortedWith(compareByDescending<IServEintrag> { it.istOrdner }.thenBy { it.name.lowercase() })
    }
}
