package com.homecursor.client.ui

import com.homecursor.client.storage.UpdateSettingsStore
import com.homecursor.client.update.AppVersion
import com.homecursor.client.update.FakeApkDownloader
import com.homecursor.client.update.FakeApkInstaller
import com.homecursor.client.update.FakeUpdateChecker
import com.homecursor.client.update.UpdateException
import com.homecursor.client.update.UpdateInfo
import com.homecursor.client.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FakeUpdateSettingsStore(override var checksEnabled: Boolean = true, override var lastCheckMillis: Long = 0L) : UpdateSettingsStore

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val release = UpdateInfo(AppVersion(1, 4, 0), "https://example.test/a.apk", "a".repeat(64), 1)
    private val checker = FakeUpdateChecker(result = release)
    private val downloader = FakeApkDownloader()
    private val installer = FakeApkInstaller()
    private val settings = FakeUpdateSettingsStore()
    private var now = 10 * UpdateViewModel.DAY_MILLIS

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = UpdateViewModel(checker, downloader, installer, settings, AppVersion(1, 3, 0), clock = { now })

    private fun UpdateViewModel.state() = uiState.value.state

    @Test
    fun onForeground_neverChecked_checksAndOffersTheRelease() = runTest {
        val viewModel = viewModel()

        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(UpdateState.Available(release), viewModel.state())
        assertEquals(now, settings.lastCheckMillis)
    }

    @Test
    fun onForeground_checkedRecently_doesNotCheck() = runTest {
        settings.lastCheckMillis = now - UpdateViewModel.HOUR_MILLIS
        val viewModel = viewModel()

        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(0, checker.calls)
        assertEquals(UpdateState.Idle, viewModel.state())
    }

    @Test
    fun onForeground_lastCheckOlderThanADay_checksAgain() = runTest {
        settings.lastCheckMillis = now - UpdateViewModel.DAY_MILLIS - 1
        val viewModel = viewModel()

        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(1, checker.calls)
    }

    @Test
    fun onForeground_checksSwitchedOff_doesNotCheck() = runTest {
        settings.checksEnabled = false
        val viewModel = viewModel()

        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(0, checker.calls)
        assertFalse(viewModel.uiState.value.checksEnabled)
    }

    @Test
    fun onForeground_silentCheckFails_staysQuietAndDoesNotRecordACheck() = runTest {
        checker.failure = UpdateException("offline")
        val viewModel = viewModel()

        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(UpdateState.Idle, viewModel.state())
        assertEquals(0L, settings.lastCheckMillis)
    }

    @Test
    fun onForeground_afterAFailedCheck_waitsBeforeTryingAgain() = runTest {
        checker.failure = UpdateException("offline")
        val viewModel = viewModel()
        viewModel.onForeground()
        advanceUntilIdle()

        now += UpdateViewModel.HOUR_MILLIS / 2
        viewModel.onForeground()
        advanceUntilIdle()
        assertEquals(1, checker.calls)

        now += UpdateViewModel.HOUR_MILLIS
        viewModel.onForeground()
        advanceUntilIdle()
        assertEquals(2, checker.calls)
    }

    @Test
    fun checkNow_ignoresTheScheduleAndTheSetting() = runTest {
        settings.checksEnabled = false
        settings.lastCheckMillis = now
        val viewModel = viewModel()

        viewModel.checkNow()
        advanceUntilIdle()

        assertEquals(UpdateState.Available(release), viewModel.state())
    }

    @Test
    fun checkNow_showsCheckingWhileItRuns() = runTest {
        val viewModel = viewModel()

        viewModel.checkNow()

        assertEquals(UpdateState.Checking, viewModel.state())
    }

    @Test
    fun checkNow_nothingNewer_saysUpToDate() = runTest {
        checker.result = null
        val viewModel = viewModel()

        viewModel.checkNow()
        advanceUntilIdle()

        assertEquals(UpdateState.UpToDate, viewModel.state())
    }

    @Test
    fun checkNow_failure_isShown() = runTest {
        checker.failure = UpdateException("offline")
        val viewModel = viewModel()

        viewModel.checkNow()
        advanceUntilIdle()

        assertEquals(UpdateState.Failed(null), viewModel.state())
    }

    @Test
    fun startUpdate_downloadsAndHandsTheApkToTheInstaller() = runTest {
        val viewModel = viewModel()
        viewModel.checkNow()
        advanceUntilIdle()

        viewModel.startUpdate()
        assertEquals(UpdateState.Downloading(release), viewModel.state())
        advanceUntilIdle()

        assertEquals(listOf(release), downloader.downloaded)
        assertEquals(listOf(File("homecursor-1.4.0.apk")), installer.installed)
        assertEquals(UpdateState.Available(release), viewModel.state())
    }

    @Test
    fun startUpdate_withoutPermission_asksForItAndDownloadsNothing() = runTest {
        installer.allowed = false
        val viewModel = viewModel()
        viewModel.checkNow()
        advanceUntilIdle()

        viewModel.startUpdate()
        advanceUntilIdle()

        assertEquals(UpdateState.NeedsPermission(release), viewModel.state())
        assertTrue(downloader.downloaded.isEmpty())
    }

    @Test
    fun onForeground_afterThePermissionWasGiven_offersTheUpdateAgain() = runTest {
        installer.allowed = false
        settings.lastCheckMillis = now
        val viewModel = viewModel()
        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.startUpdate()

        installer.allowed = true
        viewModel.onForeground()

        assertEquals(UpdateState.Available(release), viewModel.state())
    }

    @Test
    fun startUpdate_downloadFails_showsFailureWithTheRelease() = runTest {
        downloader.failure = UpdateException("bad checksum")
        val viewModel = viewModel()
        viewModel.checkNow()
        advanceUntilIdle()

        viewModel.startUpdate()
        advanceUntilIdle()

        assertEquals(UpdateState.Failed(release), viewModel.state())
        assertTrue(installer.installed.isEmpty())
        // The release is still offered, so the user can try again.
        assertEquals(release, viewModel.uiState.value.offered)
    }

    @Test
    fun startUpdate_installerCannotOpen_showsFailure() = runTest {
        installer.failure = IllegalStateException("no activity")
        val viewModel = viewModel()
        viewModel.checkNow()
        advanceUntilIdle()

        viewModel.startUpdate()
        advanceUntilIdle()

        assertEquals(UpdateState.Failed(release), viewModel.state())
    }

    @Test
    fun startUpdate_withoutARelease_doesNothing() = runTest {
        val viewModel = viewModel()

        viewModel.startUpdate()
        advanceUntilIdle()

        assertTrue(downloader.downloaded.isEmpty())
        assertNull(viewModel.uiState.value.offered)
    }

    @Test
    fun onChecksEnabledChanged_isRememberedAndShown() = runTest {
        val viewModel = viewModel()

        viewModel.onChecksEnabledChanged(false)

        assertFalse(settings.checksEnabled)
        assertFalse(viewModel.uiState.value.checksEnabled)
    }
}
