package com.homecursor.client.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homecursor.client.storage.UpdateSettingsStore
import com.homecursor.client.update.ApkDownloader
import com.homecursor.client.update.ApkInstaller
import com.homecursor.client.update.AppVersion
import com.homecursor.client.update.UpdateChecker
import com.homecursor.client.update.UpdateException
import com.homecursor.client.update.UpdateInfo
import com.homecursor.client.update.UpdateState
import com.homecursor.client.update.UpdateUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Looking for a newer release and installing it. The Activity calls [onForeground]; that checks by itself when the
 * user has not switched checks off and the last one is longer ago than [checkIntervalMillis] (after a failed one, than
 * [retryIntervalMillis]). [checkNow] checks whatever the settings say. Downloading and the system installer follow
 * [startUpdate]; the system always asks the user to confirm the install.
 */
class UpdateViewModel(
    private val checker: UpdateChecker,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
    private val settings: UpdateSettingsStore,
    installed: AppVersion?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val checkIntervalMillis: Long = DAY_MILLIS,
    private val retryIntervalMillis: Long = HOUR_MILLIS,
) : ViewModel() {
    private val mutableState = MutableStateFlow(UpdateUiState(checksEnabled = settings.checksEnabled, installed = installed))
    val uiState: StateFlow<UpdateUiState> = mutableState

    private var job: Job? = null
    private var lastAttemptMillis = 0L

    /** Called when the app comes to the foreground. */
    fun onForeground() {
        // Back from the system screen where installing was allowed (or not).
        val current = mutableState.value.state
        if (current is UpdateState.NeedsPermission && installer.canInstall()) setState(UpdateState.Available(current.update))

        val due = clock() - settings.lastCheckMillis >= checkIntervalMillis && clock() - lastAttemptMillis >= retryIntervalMillis
        if (settings.checksEnabled && due) check(silent = true)
    }

    /** Checks now, on the user's request: the result is always shown. */
    fun checkNow() = check(silent = false)

    fun onChecksEnabledChanged(enabled: Boolean) {
        settings.checksEnabled = enabled
        mutableState.update { it.copy(checksEnabled = enabled) }
    }

    /** Downloads the offered release and opens the system installer, or asks for the permission to install first. */
    fun startUpdate() {
        val update = mutableState.value.offered ?: return
        if (job?.isActive == true) return

        if (!installer.canInstall()) {
            setState(UpdateState.NeedsPermission(update))
            return
        }

        setState(UpdateState.Downloading(update))
        job = viewModelScope.launch {
            try {
                installer.install(downloader.download(update))
                // The installer is on screen now; if the user cancels it, they can start again.
                setState(UpdateState.Available(update))
            } catch (e: UpdateException) {
                setState(UpdateState.Failed(update))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The system refused to open the installer (for example, no activity handles the intent).
                setState(UpdateState.Failed(update))
            }
        }
    }

    fun openInstallPermissionSettings() = installer.openInstallPermissionSettings()

    private fun check(silent: Boolean) {
        if (job?.isActive == true) return
        lastAttemptMillis = clock()
        val previous = mutableState.value.state
        if (!silent) setState(UpdateState.Checking)

        job = viewModelScope.launch {
            try {
                val update = checker.check()
                settings.lastCheckMillis = clock()
                setState(if (update != null) UpdateState.Available(update) else if (silent) UpdateState.Idle else UpdateState.UpToDate)
            } catch (e: UpdateException) {
                // A silent check that fails says nothing: the next one tries again.
                setState(if (silent) previous else UpdateState.Failed(null))
            }
        }
    }

    private fun setState(state: UpdateState) = mutableState.update { it.copy(state = state) }

    companion object {
        const val HOUR_MILLIS = 60 * 60 * 1000L
        const val DAY_MILLIS = 24 * HOUR_MILLIS
    }
}
