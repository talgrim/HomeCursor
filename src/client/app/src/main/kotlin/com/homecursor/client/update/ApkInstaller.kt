package com.homecursor.client.update

import java.io.File

/** Hands a downloaded APK to the system installer. Installing is always confirmed by the user in the system dialog. */
interface ApkInstaller {
    /** Whether the user has allowed this app to install packages ("Install unknown apps"). */
    fun canInstall(): Boolean

    /** Opens the system screen where the user allows installing from this app. */
    fun openInstallPermissionSettings()

    fun install(apk: File)
}
