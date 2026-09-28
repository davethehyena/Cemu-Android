package info.cemu.cemu.settings.general

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

private const val TAG = "SystemImport"

sealed interface SystemImportResult {
    data class Success(val imported: List<String>) : SystemImportResult
    data object InvalidArchive : SystemImportResult
    data object NothingUsefulFound : SystemImportResult
}

object SystemImport {
    private val REQUIRED = listOf("mlc01", "cafeLibs", "otp.bin", "seeprom.bin")

    fun import(context: Context, uri: Uri): SystemImportResult {
        val filesDir = context.filesDir
        val tempDir = File(filesDir, "system_import_tmp")
        tempDir.deleteRecursively()
        if (!tempDir.mkdirs()) {
            Log.e(TAG, "Could not create temporary directory")
            return SystemImportResult.NothingUsefulFound
        }

        if (!extractZip(context, uri, tempDir)) {
            tempDir.deleteRecursively()
            return SystemImportResult.InvalidArchive
        }

        val source = findPayloadRoot(tempDir)
        val imported = mutableListOf<String>()
        for (name in REQUIRED) {
            val from = File(source, name)
            if (!from.exists()) continue
            val to = File(filesDir, name)
            if (to.exists() && !to.deleteRecursively()) {
                Log.w(TAG, "Could not replace existing $name")
                continue
            }
            if (!from.renameTo(to)) from.copyRecursively(to, overwrite = true)
            imported.add(name)
            Log.i(TAG, "Imported $name")
        }
        tempDir.deleteRecursively()

        return if (imported.isEmpty()) {
            SystemImportResult.NothingUsefulFound
        } else {
            SystemImportResult.Success(imported)
        }
    }

    private fun extractZip(context: Context, uri: Uri, dest: File): Boolean {
        val stream = context.contentResolver.openInputStream(uri) ?: return false
        val root = dest.canonicalFile
        var entries = 0
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val canonical = File(root, entry.name).canonicalFile
                // zip slip: una entrada con .. podria escribir fuera de nuestra carpeta
                val inside = canonical == root || canonical.path.startsWith(root.path + File.separator)
                if (!inside) {
                    Log.w(TAG, "Skipping unsafe entry: ${entry.name}")
                } else if (entry.isDirectory) {
                    canonical.mkdirs()
                } else {
                    canonical.parentFile?.mkdirs()
                    canonical.outputStream().use { zip.copyTo(it, 64 * 1024) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
                entries++
            }
        }
        return entries > 0
    }

    // Los archives suelen venir con una carpeta raiz envolviendo todo el contenido
    private fun findPayloadRoot(tempDir: File): File {
        if (hasPayload(tempDir)) return tempDir
        val dirs = tempDir.listFiles()?.filter { it.isDirectory } ?: return tempDir
        if (dirs.size == 1 && hasPayload(dirs[0])) return dirs[0]
        return tempDir
    }

    private fun hasPayload(dir: File) =
        File(dir, "mlc01").exists() || File(dir, "cafeLibs").exists()
}
