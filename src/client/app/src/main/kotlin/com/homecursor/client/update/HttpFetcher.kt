package com.homecursor.client.update

import java.io.File
import java.io.IOException

/** Blocking HTTP GET; call it off the main thread. Both functions throw [IOException] when the request fails. */
interface HttpFetcher {
    fun getText(url: String): String

    /** Writes the response body to [target], replacing it. */
    fun download(url: String, target: File)
}
