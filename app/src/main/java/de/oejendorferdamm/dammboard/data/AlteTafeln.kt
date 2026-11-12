package de.oejendorferdamm.dammboard.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Alte Tafeln (.ubmx2) auf dem ganzen Board suchen – dafür braucht DammBoard einmal „Zugriff auf alle Dateien“
 * (bis Android 10: Speicher lesen). Gefundenes kommt in den Ordner „Importiert“; schon Importiertes wird übersprungen.
 */
object AlteTafeln {
    const val ORDNER = "Importiert"

    fun erlaubt(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Ab Android 11: Einstellungsseite „Zugriff auf alle Dateien“ für DammBoard (null = Laufzeit-Erlaubnis nötig) */
    fun erlaubnisSeite(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val eigen = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
        return if (eigen.resolveActivity(context.packageManager) != null) eigen else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    /** Speicher des Boards und eingesteckte USB-Sticks */
    fun wurzeln(): List<File> {
        val l = ArrayList<File>()
        @Suppress("DEPRECATION") l.add(Environment.getExternalStorageDirectory())
        File("/storage").listFiles()?.filter { it.isDirectory && it.canRead() && it.name != "self" && it.name != "emulated" }?.let { l.addAll(it) }
        return l.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.path) }
    }

    /** Alle .ubmx2 unter [start] (ohne versteckte Ordner, höchstens [maxTiefe] Ebenen und [max] Dateien) */
    fun suche(start: List<File>, maxTiefe: Int = 10, max: Int = 3000): List<File> {
        val gefunden = ArrayList<File>()
        val gesehen = HashSet<String>()
        fun gehe(d: File, tiefe: Int) {
            if (tiefe > maxTiefe || gefunden.size >= max) return
            val pfad = runCatching { d.canonicalPath }.getOrDefault(d.path)
            if (!gesehen.add(pfad)) return
            val kinder = try { d.listFiles() } catch (_: SecurityException) { null } ?: return
            for (k in kinder) {
                if (gefunden.size >= max) return
                if (k.isDirectory) { if (!k.name.startsWith(".")) gehe(k, tiefe + 1) }
                else if (k.name.endsWith(UbmxImport.ENDUNG, ignoreCase = true) && k.length() > 0) gefunden.add(k)
            }
        }
        start.filter { it.exists() }.forEach { if (it.isFile) { if (it.name.endsWith(UbmxImport.ENDUNG, true)) gefunden.add(it) } else gehe(it, 0) }
        return gefunden
    }

    private fun merkmal(f: File) = "${f.absolutePath}|${f.length()}|${f.lastModified()}"

    /** Noch nicht importierte Dateien */
    fun neu(context: Context, dateien: List<File>): List<File> {
        val schon = context.getSharedPreferences("alteTafeln", Context.MODE_PRIVATE).getStringSet("importiert", emptySet()) ?: emptySet()
        return dateien.filter { merkmal(it) !in schon }
    }

    fun merke(context: Context, dateien: List<File>) {
        val p = context.getSharedPreferences("alteTafeln", Context.MODE_PRIVATE)
        val alt = p.getStringSet("importiert", emptySet()) ?: emptySet()
        p.edit().putStringSet("importiert", alt + dateien.map(::merkmal)).apply()
    }
}
