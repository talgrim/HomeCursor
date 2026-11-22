package com.homecursor.client.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseManifestTest {
    private val manifestUrl = "https://example.test/files/manifest.json"
    private val hash = "a".repeat(64)

    private fun entry(
        product: String = "homecursor",
        version: String = "1.3.0",
        suffix: String = ".apk",
        file: String = "$product-$version$suffix",
        sha256: String = hash,
        size: Long = 100,
    ) = """{"product":"$product","version":"$version","suffix":"$suffix","file":"$file","size":$size,"sha256":"$sha256","latest":false}"""

    private fun manifest(vararg entries: String) = """{"generated":"x","files":[${entries.joinToString(",")}]}"""

    private fun find(json: String) = ReleaseManifest.findLatest(json, manifestUrl, "homecursor", ".apk")

    @Test
    fun findLatest_returnsTheHighestVersionOfTheProduct() {
        val result = find(manifest(entry(version = "1.2.0"), entry(version = "1.10.0"), entry(version = "1.3.0")))

        assertEquals(AppVersion(1, 10, 0), result?.version)
    }

    @Test
    fun findLatest_resolvesTheDownloadNextToTheManifest() {
        val result = find(manifest(entry()))

        assertEquals("https://example.test/files/homecursor-1.3.0.apk", result?.downloadUrl)
        assertEquals(hash, result?.sha256)
        assertEquals(100L, result?.size)
    }

    @Test
    fun findLatest_ignoresOtherProductsAndSuffixes() {
        val result = find(
            manifest(
                entry(product = "homecursor-host", suffix = "-win-x64.zip", version = "9.0.0"),
                entry(product = "other", version = "9.0.0"),
                entry(version = "1.3.0"),
            ),
        )

        assertEquals(AppVersion(1, 3, 0), result?.version)
    }

    @Test
    fun findLatest_withoutMatchingEntry_returnsNull() {
        assertNull(find(manifest(entry(product = "other"))))
        assertNull(find(manifest()))
        assertNull(find("""{"nofiles":true}"""))
    }

    @Test
    fun findLatest_skipsMalformedEntries() {
        val result = find(
            manifest(
                entry(version = "nope"),
                entry(version = "2.0.0", sha256 = "short"),
                entry(version = "3.0.0", file = "../evil.apk"),
                entry(version = "4.0.0", file = "https:evil.apk"),
                entry(version = "1.3.0"),
            ),
        )

        assertEquals(AppVersion(1, 3, 0), result?.version)
    }

    @Test(expected = UpdateException::class)
    fun findLatest_invalidJson_throws() {
        find("<html>")
    }
}
