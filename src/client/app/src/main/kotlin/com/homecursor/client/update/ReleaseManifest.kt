package com.homecursor.client.update

import org.json.JSONException
import org.json.JSONObject
import java.net.URI

/** Reads the release manifest: `{"files": [{"product", "version", "suffix", "file", "sha256", "size", ...}]}`. */
object ReleaseManifest {
    /**
     * The entry with the highest version for [product] and [suffix], or null if there is none. Malformed entries
     * (no valid version, no SHA-256, a file name that is not a plain name) are skipped. Files are served next to
     * the manifest at [manifestUrl].
     */
    fun findLatest(json: String, manifestUrl: String, product: String, suffix: String): UpdateInfo? {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw UpdateException("The release manifest is not valid", e)
        }
        val files = root.optJSONArray("files") ?: return null

        var best: UpdateInfo? = null
        for (index in 0 until files.length()) {
            val entry = files.optJSONObject(index) ?: continue
            if (entry.optString("product") != product || entry.optString("suffix") != suffix) continue

            val version = AppVersion.parse(entry.optString("version")) ?: continue
            val file = entry.optString("file")
            val sha256 = entry.optString("sha256")
            if (!isPlainFileName(file) || !isSha256(sha256)) continue
            if (best != null && version <= best.version) continue

            best = UpdateInfo(version, URI(manifestUrl).resolve(file).toString(), sha256, entry.optLong("size", 0))
        }
        return best
    }

    private fun isSha256(text: String) = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    // The file is resolved next to the manifest, so it must not be able to point anywhere else.
    private fun isPlainFileName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it in "/\\:?#%" || it.isWhitespace() }
}
