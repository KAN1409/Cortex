package com.kareem.cortex.prime.backup

import android.content.Context
import android.net.Uri
import com.kareem.cortex.prime.data.db.CortexDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Owner-triggered backup to any document provider (e.g. Google Drive via the system picker). Nothing uploads by itself. */
class BackupManager(private val context: Context, private val db: CortexDatabase) {
    private fun roots(): List<Pair<String, File>> = listOf(
        "db" to context.getDatabasePath(CortexDatabase.NAME).parentFile!!,
        "files/originals" to File(context.filesDir, "originals"),
        "prefs" to File(context.applicationInfo.dataDir, "shared_prefs"),
    )

    fun export(target: Uri, pass: CharArray) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val zip = File.createTempFile("backup", ".zip", context.cacheDir)
        try {
            ZipOutputStream(zip.outputStream().buffered()).use { z ->
                for ((name, dir) in roots()) dir.walkTopDown().filter { it.isFile && !it.name.endsWith("-journal") }.forEach { f ->
                    z.putNextEntry(ZipEntry("$name/" + f.relativeTo(dir).path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
                }
            }
            context.contentResolver.openOutputStream(target, "w")!!.use { out -> zip.inputStream().use { BackupCrypto.encrypt(it, out, pass) } }
        } finally { zip.delete() }
    }

    /** Decrypts and validates first; only then replaces local data. The app restarts afterwards. */
    fun restore(source: Uri, pass: CharArray) {
        val zip = File.createTempFile("restore", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source)!!.use { input -> zip.outputStream().use { BackupCrypto.decrypt(input, it, pass) } }
            val staging = File(context.cacheDir, "restore-staging").apply { deleteRecursively(); mkdirs() }
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val out = File(staging, e.name).canonicalFile
                    require(out.path.startsWith(staging.canonicalPath)) { "unsafe entry" }
                    out.parentFile?.mkdirs(); out.outputStream().use { z.copyTo(it) }
                }
            }
            require(File(staging, "db/${CortexDatabase.NAME}").isFile) { "backup has no database" }
            db.close()
            for ((name, dir) in roots()) { val src = File(staging, name); if (src.isDirectory) { dir.deleteRecursively(); src.copyRecursively(dir, overwrite = true) } }
            staging.deleteRecursively()
        } finally { zip.delete() }
    }
}
