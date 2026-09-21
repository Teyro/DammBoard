package de.oejendorferdamm.dammboard.data

import de.oejendorferdamm.dammboard.model.IServEintrag
import de.oejendorferdamm.dammboard.model.IServZugang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

private const val PROPFIND_KOERPER = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:displayname/></d:prop></d:propfind>"""

/** Einfacher WebDAV-Client für den IServ-Dateispeicher: Ordner auflisten und Dateien hochladen. */
class IServClient(private val zugang: IServZugang) {

    private val client = OkHttpClient()

    private fun basisPfad(): String {
        val ohneSchema = zugang.serverUrl.substringAfter("://")
        val index = ohneSchema.indexOf('/')
        return if (index == -1) "" else ohneSchema.substring(index).trimEnd('/')
    }

    private fun volleUrl(pfad: String): String {
        val normalisiert = if (pfad.startsWith("/")) pfad else "/$pfad"
        return zugang.serverUrl.trimEnd('/') + normalisiert
    }

    suspend fun liste(pfad: String): Result<List<IServEintrag>> = withContext(Dispatchers.IO) {
        try {
            val angefragterPfad = if (pfad.startsWith("/")) pfad else "/$pfad"
            val anfrage = Request.Builder()
                .url(volleUrl(angefragterPfad))
                .header("Authorization", Credentials.basic(zugang.benutzername, zugang.passwort))
                .header("Depth", "1")
                .method("PROPFIND", PROPFIND_KOERPER.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .build()
            client.newCall(anfrage).execute().use { antwort ->
                if (!antwort.isSuccessful) {
                    return@withContext Result.failure(IOException("IServ antwortete mit HTTP ${antwort.code}"))
                }
                val text = antwort.body?.string()
                    ?: return@withContext Result.failure(IOException("Leere Antwort von IServ"))
                Result.success(parsePropfind(text, basisPfad(), angefragterPfad))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun hochladen(pfad: String, dateiname: String, bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ordner = if (pfad.startsWith("/")) pfad else "/$pfad"
            val ordnerMitSchraegstrich = if (ordner.endsWith("/")) ordner else "$ordner/"
            val kodierterDateiname = URLEncoder.encode(dateiname, "UTF-8").replace("+", "%20")
            val anfrage = Request.Builder()
                .url(volleUrl(ordnerMitSchraegstrich) + kodierterDateiname)
                .header("Authorization", Credentials.basic(zugang.benutzername, zugang.passwort))
                .put(bytes.toRequestBody("image/png".toMediaType()))
                .build()
            client.newCall(anfrage).execute().use { antwort ->
                if (antwort.isSuccessful) Result.success(Unit)
                else Result.failure(IOException("Hochladen fehlgeschlagen: HTTP ${antwort.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePropfind(xml: String, basisPfad: String, angefragterPfad: String): List<IServEintrag> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val dokument = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val antworten = dokument.getElementsByTagNameNS("DAV:", "response")
        val angefragtNormalisiert = angefragterPfad.trimEnd('/')
        val ergebnis = mutableListOf<IServEintrag>()

        for (i in 0 until antworten.length) {
            val knoten = antworten.item(i) as? Element ?: continue
            val hrefKnoten = knoten.getElementsByTagNameNS("DAV:", "href").item(0) ?: continue
            val hrefRoh = hrefKnoten.textContent ?: continue
            val href = try {
                URLDecoder.decode(hrefRoh, "UTF-8")
            } catch (e: Exception) {
                hrefRoh
            }
            val istOrdner = knoten.getElementsByTagNameNS("DAV:", "collection").length > 0
            var relativ = href.removePrefix(basisPfad)
            if (!relativ.startsWith("/")) relativ = "/$relativ"
            val normalisiert = relativ.trimEnd('/')
            if (normalisiert == angefragtNormalisiert) continue
            val name = normalisiert.substringAfterLast('/')
            if (name.isBlank()) continue
            ergebnis.add(IServEintrag(name = name, pfad = normalisiert, istOrdner = istOrdner))
        }

        return ergebnis.sortedWith(compareByDescending<IServEintrag> { it.istOrdner }.thenBy { it.name.lowercase() })
    }
}
