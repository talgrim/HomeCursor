package com.homecursor.client.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** [ApkInstaller] on the system package installer, with the APK shared through a [FileProvider]. */
class SystemApkInstaller(private val context: Context) : ApkInstaller {
    override fun canInstall() = context.packageManager.canRequestPackageInstalls()

    override fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, authority(context), apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        /** Directory (inside the cache) the updates are downloaded to; keep in sync with `res/xml/update_file_paths.xml`. */
        const val DIRECTORY_NAME = "updates"

        fun authority(context: Context) = "${context.packageName}.updates"
    }
}
