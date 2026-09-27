package de.oejendorferdamm.dammboard.ui.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Ergebnis der Prüfung einer heruntergeladenen Update-APK vor der Installation. */
enum class ApkPruefung {
    /** Passt – der Installer kann starten. */
    IN_ORDNUNG,

    /**
     * Die neue Version ist anders signiert als die installierte. Das passiert, wenn auf dem Gerät
     * noch eine sehr alte Testversion läuft: Android verweigert dann das Update mit "App nicht
     * installiert". Abhilfe: DammBoard einmal deinstallieren und neu installieren.
     */
    ANDERE_SIGNATUR,

    /** Keine gültige DammBoard-Installationsdatei. */
    UNGUELTIG
}

/**
 * Prüft die heruntergeladene APK: richtiges Paket und dieselbe Signatur wie die installierte App.
 * Lässt sich die Signatur auf einem Gerät nicht auslesen, entscheidet wie bisher der Installer.
 */
fun pruefeUpdateApk(context: Context, apk: File): ApkPruefung {
    return try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archiv = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return ApkPruefung.UNGUELTIG
        if (archiv.packageName != context.packageName) return ApkPruefung.UNGUELTIG
        val installiert = pm.getPackageInfo(context.packageName, flags)
        val neu = signaturenVon(archiv)
        val alt = signaturenVon(installiert)
        when {
            neu.isEmpty() || alt.isEmpty() -> ApkPruefung.IN_ORDNUNG
            neu == alt -> ApkPruefung.IN_ORDNUNG
            else -> ApkPruefung.ANDERE_SIGNATUR
        }
    } catch (e: Exception) {
        ApkPruefung.IN_ORDNUNG
    }
}

@Suppress("DEPRECATION")
private fun signaturenVon(info: PackageInfo): Set<String> {
    val signaturen: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners
    } else {
        info.signatures
    }
    return signaturen.orEmpty().map { it.toCharsString() }.toSet()
}

/** Baut den System-Intent, der den Paketinstaller mit der heruntergeladenen APK öffnet. */
fun installationsIntentFuer(context: Context, apkDatei: File): Intent {
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkDatei)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Ob DammBoard aktuell die Erlaubnis hat, Pakete aus unbekannten Quellen zu installieren. */
fun kannUnbekannteQuellenInstallieren(context: Context): Boolean =
    context.packageManager.canRequestPackageInstalls()

/** Öffnet den Systemdialog, in dem der Nutzer DammBoard die Installationserlaubnis erteilen kann. */
fun oeffneUnbekannteQuellenEinstellungen(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
