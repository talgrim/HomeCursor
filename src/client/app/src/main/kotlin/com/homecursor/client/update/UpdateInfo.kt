package com.homecursor.client.update

/** A released client version that can be downloaded. [sha256] is hex, [size] is in bytes. */
data class UpdateInfo(val version: AppVersion, val downloadUrl: String, val sha256: String, val size: Long)

/** Looking for, downloading or checking an update went wrong. */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)
