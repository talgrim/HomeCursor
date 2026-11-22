package com.homecursor.client.update

/** What the app knows about updates. */
sealed interface UpdateState {
    /** Nothing to report: no check yet, or the app is up to date. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** A check the user asked for found nothing newer. */
    data object UpToDate : UpdateState

    data class Available(val update: UpdateInfo) : UpdateState

    data class Downloading(val update: UpdateInfo) : UpdateState

    /** Installing needs the user to allow it for this app first ("Install unknown apps"). */
    data class NeedsPermission(val update: UpdateInfo) : UpdateState

    /** [update] is set when a release is known (a download failed), null when the check itself failed. */
    data class Failed(val update: UpdateInfo?) : UpdateState
}

/** Everything the screens show about updating. [installed] is the running version. */
data class UpdateUiState(
    val state: UpdateState = UpdateState.Idle,
    val checksEnabled: Boolean = true,
    val installed: AppVersion? = null,
) {
    /** The release that can be installed, if one is known and not yet being worked on by something that excludes it. */
    val offered: UpdateInfo?
        get() = when (val s = state) {
            is UpdateState.Available -> s.update
            is UpdateState.Downloading -> s.update
            is UpdateState.NeedsPermission -> s.update
            is UpdateState.Failed -> s.update
            else -> null
        }
}
