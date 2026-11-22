package com.homecursor.client.update

import java.io.File
import java.io.IOException

/** Serves canned answers; [failure] makes every request fail. */
class FakeHttpFetcher(var text: String = "", var body: ByteArray = ByteArray(0), var failure: IOException? = null) : HttpFetcher {
    val requests = mutableListOf<String>()

    override fun getText(url: String): String {
        requests += url
        failure?.let { throw it }
        return text
    }

    override fun download(url: String, target: File) {
        requests += url
        failure?.let { throw it }
        target.writeBytes(body)
    }
}

class FakeUpdateChecker(var result: UpdateInfo? = null, var failure: UpdateException? = null) : UpdateChecker {
    var calls = 0

    override suspend fun check(): UpdateInfo? {
        calls++
        failure?.let { throw it }
        return result
    }
}

class FakeApkDownloader(var failure: UpdateException? = null) : ApkDownloader {
    val downloaded = mutableListOf<UpdateInfo>()

    override suspend fun download(update: UpdateInfo): File {
        downloaded += update
        failure?.let { throw it }
        return File("homecursor-${update.version}.apk")
    }
}

class FakeApkInstaller(var allowed: Boolean = true, var failure: RuntimeException? = null) : ApkInstaller {
    val installed = mutableListOf<File>()
    var permissionSettingsOpened = 0

    override fun canInstall() = allowed

    override fun openInstallPermissionSettings() {
        permissionSettingsOpened++
    }

    override fun install(apk: File) {
        failure?.let { throw it }
        installed += apk
    }
}
