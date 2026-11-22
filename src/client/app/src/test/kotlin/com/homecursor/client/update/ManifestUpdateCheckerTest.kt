package com.homecursor.client.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ManifestUpdateCheckerTest {
    private fun manifest(version: String) =
        """{"files":[{"product":"homecursor","version":"$version","suffix":".apk","file":"homecursor-$version.apk","size":1,"sha256":"${"b".repeat(64)}"}]}"""

    private fun checker(fetcher: FakeHttpFetcher, installed: AppVersion = AppVersion(1, 3, 0)) =
        ManifestUpdateChecker(fetcher, installed, "https://example.test/files/manifest.json", Dispatchers.Unconfined)

    @Test
    fun check_newerRelease_isReturned() = runTest {
        val result = checker(FakeHttpFetcher(text = manifest("1.4.0"))).check()

        assertEquals(AppVersion(1, 4, 0), result?.version)
    }

    @Test
    fun check_sameOrOlderRelease_returnsNull() = runTest {
        assertNull(checker(FakeHttpFetcher(text = manifest("1.3.0"))).check())
        assertNull(checker(FakeHttpFetcher(text = manifest("1.2.0"))).check())
    }

    @Test
    fun check_asksTheConfiguredManifest() = runTest {
        val fetcher = FakeHttpFetcher(text = manifest("1.3.0"))

        checker(fetcher).check()

        assertEquals(listOf("https://example.test/files/manifest.json"), fetcher.requests)
    }

    @Test(expected = UpdateException::class)
    fun check_networkFailure_throwsUpdateException() = runTest {
        checker(FakeHttpFetcher(failure = IOException("offline"))).check()
    }

    @Test(expected = UpdateException::class)
    fun check_garbageManifest_throwsUpdateException() = runTest {
        checker(FakeHttpFetcher(text = "<html>")).check()
    }
}
