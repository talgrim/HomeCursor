package com.homecursor.client.update

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

interface ApkDownloader {
    /** Downloads the APK of [update] and returns the file. Throws [UpdateException] if the download fails or does not match its checksum. */
    suspend fun download(update: UpdateInfo): File
}

/** Downloads into [directory] and only hands out files whose SHA-256 matches the manifest. Older downloads are removed first. */
class VerifyingApkDownloader(
    private val fetcher: HttpFetcher,
    private val directory: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ApkDownloader {
    override suspend fun download(update: UpdateInfo): File = withContext(dispatcher) {
        directory.mkdirs()
        directory.listFiles()?.forEach { it.delete() }

        val partial = File(directory, "homecursor-${update.version}.apk.part")
        val apk = File(directory, "homecursor-${update.version}.apk")
        try {
            fetcher.download(update.downloadUrl, partial)
        } catch (e: IOException) {
            partial.delete()
            throw UpdateException("Could not download the update", e)
        }

        if (!sha256Of(partial).equals(update.sha256, ignoreCase = true)) {
            partial.delete()
            throw UpdateException("The downloaded update does not match its checksum")
        }
        if (!partial.renameTo(apk)) {
            partial.delete()
            throw UpdateException("Could not store the downloaded update")
        }
        apk
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
