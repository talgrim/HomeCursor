package com.homecursor.client.update

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface UpdateChecker {
    /** The newest release if it is newer than the installed version, otherwise null. Throws [UpdateException] if the check fails. */
    suspend fun check(): UpdateInfo?
}

/** Finds a newer release in the release manifest. */
class ManifestUpdateChecker(
    private val fetcher: HttpFetcher,
    private val installed: AppVersion,
    private val manifestUrl: String = DEFAULT_MANIFEST_URL,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateChecker {
    override suspend fun check(): UpdateInfo? = withContext(dispatcher) {
        val json = try {
            fetcher.getText(manifestUrl)
        } catch (e: IOException) {
            throw UpdateException("Could not read the release manifest", e)
        }
        ReleaseManifest.findLatest(json, manifestUrl, PRODUCT, SUFFIX)?.takeIf { it.version > installed }
    }

    companion object {
        const val DEFAULT_MANIFEST_URL = "https://wip.talgrim.dev/files/manifest.json"
        const val PRODUCT = "homecursor"
        const val SUFFIX = ".apk"
    }
}
