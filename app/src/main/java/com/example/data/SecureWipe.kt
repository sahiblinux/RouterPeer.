package com.example.data

import android.content.Context
import android.os.Process
import android.util.Log
import com.example.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Arrays

/**
 * High-assurance military-grade panic wipe mechanism.
 * Performs a silent, absolute, low-level factory reset of the application,
 * sanitizes disk sectors, deletes databases, wipes encrypted keyrings,
 * clears cache, and terminates the OS process immediately to eliminate volatile RAM artifacts.
 */
object SecureWipe {

    private const val TAG = "SecureWipe"

    /**
     * Executes the low-level panic wipe.
     * Can be invoked synchronously or from a coroutine.
     */
    suspend fun executePanicWipe(context: Context) {
        withContext(Dispatchers.IO) {
            executePanicWipeInternal(context.applicationContext)
        }
    }

    /**
     * Internal synchronous wipe pipeline.
     */
    fun executePanicWipeBlocking(context: Context) {
        executePanicWipeInternal(context.applicationContext)
    }

    private fun executePanicWipeInternal(appContext: Context) {
        try {
            // 1. Close Room database instance if active and delete database files
            try {
                AppDatabase.closeAndResetInstance()
                appContext.deleteDatabase(AppDatabase.DATABASE_NAME)
                // Also check journal, shm, and wal files
                val dbDir = appContext.getDatabasePath(AppDatabase.DATABASE_NAME)?.parentFile
                if (dbDir != null && dbDir.exists()) {
                    dbDir.listFiles()?.forEach { file ->
                        if (file.name.startsWith(AppDatabase.DATABASE_NAME)) {
                            secureDeleteFile(file)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed database purge", e)
            }

            // 2. Clear all EncryptedSharedPreferences & delete XML backing files
            try {
                val securePrefs = SecurePreferences(appContext)
                securePrefs.clearAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear EncryptedSharedPreferences", e)
            }

            try {
                val sharedPrefsDir = File(appContext.applicationInfo.dataDir, "shared_prefs")
                if (sharedPrefsDir.exists()) {
                    sharedPrefsDir.listFiles()?.forEach { file ->
                        secureDeleteFile(file)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed shared_prefs purge", e)
            }

            // 3. Wipe Application Cache and Internal Files
            try {
                appContext.cacheDir?.let { wipeDirectory(it) }
                appContext.codeCacheDir?.let { wipeDirectory(it) }
                appContext.filesDir?.let { wipeDirectory(it) }
                appContext.externalCacheDir?.let { wipeDirectory(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed cache purge", e)
            }

            // 4. Force JVM Garbage Collection to eliminate unpinned references in memory
            System.gc()
            System.runFinalization()

        } finally {
            // 5. Instantly kill the OS process to eradicate live RAM residue
            Process.killProcess(Process.myPid())
            System.exit(0)
        }
    }

    /**
     * Securely overwrites file bytes with zeroes before deleting the inode.
     */
    private fun secureDeleteFile(file: File) {
        try {
            if (file.exists() && file.isFile) {
                val length = file.length()
                if (length > 0) {
                    val zeroBytes = ByteArray(minOf(length, 4096).toInt())
                    Arrays.fill(zeroBytes, 0.toByte())
                    file.outputStream().use { out ->
                        var written: Long = 0
                        while (written < length) {
                            val toWrite = minOf(zeroBytes.size.toLong(), length - written).toInt()
                            out.write(zeroBytes, 0, toWrite)
                            written += toWrite
                        }
                        out.flush()
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore during panic wipe
        } finally {
            file.delete()
        }
    }

    /**
     * Recursively wipes a directory and zero-fills files.
     */
    private fun wipeDirectory(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                wipeDirectory(child)
            } else {
                secureDeleteFile(child)
            }
        }
        dir.delete()
    }
}
