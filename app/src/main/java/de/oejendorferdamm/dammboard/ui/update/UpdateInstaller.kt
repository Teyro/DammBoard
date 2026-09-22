package de.oejendorferdamm.dammboard.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

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
