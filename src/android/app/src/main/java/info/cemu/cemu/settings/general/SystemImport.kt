package info.cemu.cemu.settings.general

import android.content.Context
import android.net.Uri
import info.cemu.cemu.common.android.context.internalFolder
import info.cemu.cemu.nativeinterface.NativeLogging
import java.io.File
import java.util.zip.ZipInputStream

private fun log(message: String) {
    // android.util.Log solo va a logcat, que el usuario no puede leer desde el
    // movil. NativeLogging escribe en el mismo log.txt que se puede exportar.
    NativeLogging.log("SystemImport: $message")
}

sealed interface SystemImportResult {
    data class Success(val imported: List<String>) : SystemImportResult
    data class Failed(val reason: String) : SystemImportResult
    data object InvalidArchive : SystemImportResult
    data object NothingUsefulFound : SystemImportResult
}

object SystemImport {
    private val REQUIRED = listOf("mlc01", "cafeLibs", "otp.bin", "seeprom.bin")

    fun import(context: Context, uri: Uri): SystemImportResult {
        // Ojo: tiene que ser internalFolder() y no filesDir. Cemu usa
        // getExternalFilesDir() como userDataPath (CemuApplication:151), asi que
        // escribir en filesDir deja los archivos donde el emulador no los mira.
        val filesDir = context.internalFolder()
        log("start, uri=$uri, dataFolder=${filesDir.absolutePath}")
        if (!filesDir.isDirectory) {
            log("FAIL data folder missing")
            return SystemImportResult.Failed("Data folder is missing: ${filesDir.absolutePath}")
        }

        val tempDir = File(filesDir, "system_import_tmp")
        tempDir.deleteRecursively()
        if (!tempDir.mkdirs()) {
            log("FAIL cannot create temp dir")
            return SystemImportResult.Failed("Could not create a temporary folder")
        }

        return try {
            if (!extractZip(context, uri, tempDir)) {
                log("FAIL not a readable archive")
                SystemImportResult.InvalidArchive
            } else {
                val result = movePayload(tempDir, filesDir)
                log("result=$result")
                result
            }
        } catch (e: Exception) {
            log("FAIL exception: $e")
            SystemImportResult.Failed(e.toString())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun movePayload(tempDir: File, filesDir: File): SystemImportResult {
        val source = findPayloadRoot(tempDir)
        log("payload root=${source.absolutePath}")
        val imported = mutableListOf<String>()
        for (name in REQUIRED) {
            val from = File(source, name)
            if (!from.exists()) continue
            val to = File(filesDir, name)
            if (to.exists() && !to.deleteRecursively()) {
                log("could not replace existing $name")
                continue
            }
            if (!from.renameTo(to)) from.copyRecursively(to, overwrite = true)
            imported.add(name)
            log("imported $name into ${to.absolutePath}")
        }
        return if (imported.isEmpty()) {
            SystemImportResult.NothingUsefulFound
        } else {
            SystemImportResult.Success(imported)
        }
    }

    private fun extractZip(context: Context, uri: Uri, dest: File): Boolean {
        val stream = context.contentResolver.openInputStream(uri)
        if (stream == null) {
            log("openInputStream returned null")
            return false
        }
        val root = dest.canonicalFile
        var entries = 0
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val canonical = File(root, entry.name).canonicalFile
                // zip slip: una entrada con .. podria escribir fuera de nuestra carpeta
                val inside = canonical == root || canonical.path.startsWith(root.path + File.separator)
                if (!inside) {
                    log("skipping unsafe entry: ${entry.name}")
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
        log("extracted $entries entries")
        return entries > 0
    }

    // Los archives suelen venir con una carpeta raiz envolviendo todo el contenido
    private fun findPayloadRoot(tempDir: File): File {
        if (hasPayload(tempDir)) return tempDir
        val dirs = tempDir.listFiles()?.filter { it.isDirectory } ?: return tempDir
        if (dirs.size == 1 && hasPayload(dirs[0])) return dirs[0]
        return tempDir
    }

    private fun hasPayload(dir: File): Boolean {
        val found = File(dir, "mlc01").exists() || File(dir, "cafeLibs").exists()
        if (found) log("payload found in ${dir.absolutePath}")
        return found
    }
}
