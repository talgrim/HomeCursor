package com.homecursor.client.update

/** A release version, major.minor.patch, the way `versionName` and the release manifest write it. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        /** Parses "1.3.0"; anything that is not three non-negative numbers separated by dots gives null. */
        fun parse(text: String?): AppVersion? {
            val parts = text?.split('.') ?: return null
            if (parts.size != 3) return null
            val numbers = parts.map { part -> part.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull() ?: return null }
            return AppVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}
