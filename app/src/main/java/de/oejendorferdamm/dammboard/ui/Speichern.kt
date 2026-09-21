package de.oejendorferdamm.dammboard.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/** Schreibt das Tafelbild als PNG in die Galerie (Pictures/DammBoard) und gibt dessen Uri zurück. */
fun speichereBildUndGibUriZurueck(context: Context, bitmap: Bitmap): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        speichereUeberScopedStorage(context, bitmap)
    } else {
        speichereLegacy(context, bitmap)
    }
}

/** Ab Android 10: Scoped Storage, kein Berechtigungsdialog nötig. */
private fun speichereUeberScopedStorage(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val name = "DammBoard_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DammBoard")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        uri
    } catch (e: Exception) {
        null
    }
}

/**
 * Android 8.0–9 (API 26–28): kein Scoped Storage. Schreibt direkt nach
 * Pictures/DammBoard und trägt die Datei zusätzlich klassisch in die MediaStore-Galerie ein.
 * Setzt voraus, dass WRITE_EXTERNAL_STORAGE bereits gewährt wurde (siehe TafelScreen.kt).
 */
@Suppress("DEPRECATION")
private fun speichereLegacy(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val ordner = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DammBoard")
        if (!ordner.exists() && !ordner.mkdirs()) return null
        val datei = File(ordner, "DammBoard_${System.currentTimeMillis()}.png")
        FileOutputStream(datei).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, datei.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATA, datei.absolutePath)
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    } catch (e: Exception) {
        null
    }
}

fun teileBild(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Tafelbild teilen"))
}
