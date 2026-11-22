package com.homecursor.client.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

class VerifyingApkDownloaderTest {
    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("updates").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private val apk = "apk bytes".toByteArray()

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun info(sha256: String = sha256(apk)) = UpdateInfo(AppVersion(1, 4, 0), "https://example.test/a.apk", sha256, apk.size.toLong())

    private fun downloader(fetcher: FakeHttpFetcher, target: File = File(directory, "updates")) =
        VerifyingApkDownloader(fetcher, target, Dispatchers.Unconfined)

    @Test
    fun download_matchingChecksum_returnsTheFile() = runTest {
        val file = downloader(FakeHttpFetcher(body = apk)).download(info())

        assertEquals("homecursor-1.4.0.apk", file.name)
        assertEquals("apk bytes", file.readText())
    }

    @Test
    fun download_acceptsAnUppercaseChecksum() = runTest {
        val file = downloader(FakeHttpFetcher(body = apk)).download(info(sha256(apk).uppercase()))

        assertTrue(file.exists())
    }

    @Test
    fun download_checksumMismatch_throwsAndLeavesNothingBehind() = runTest {
        val target = File(directory, "updates")

        try {
            downloader(FakeHttpFetcher(body = apk), target).download(info("0".repeat(64)))
            org.junit.Assert.fail("expected an UpdateException")
        } catch (_: UpdateException) {
        }

        assertEquals(emptyList<String>(), target.list()?.toList())
    }

    @Test
    fun download_networkFailure_throwsUpdateException() = runTest {
        try {
            downloader(FakeHttpFetcher(failure = IOException("offline"))).download(info())
            org.junit.Assert.fail("expected an UpdateException")
        } catch (_: UpdateException) {
        }
    }

    @Test
    fun download_removesEarlierDownloads() = runTest {
        val target = File(directory, "updates").apply { mkdirs() }
        val old = File(target, "homecursor-1.3.0.apk").apply { writeText("old") }

        downloader(FakeHttpFetcher(body = apk), target).download(info())

        assertFalse(old.exists())
    }
}
