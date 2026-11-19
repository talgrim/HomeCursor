package com.homecursor.client

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.homecursor.client.connection.TcpHostConnector
import com.homecursor.client.discovery.NetworkInterfaceBroadcastTargets
import com.homecursor.client.discovery.UdpHostDiscovery
import com.homecursor.client.input.InputSender
import com.homecursor.client.input.TrackpadTuning
import com.homecursor.client.session.ConnectionCoordinator
import com.homecursor.client.storage.SharedPrefsSavedHostStore
import com.homecursor.client.storage.SharedPrefsShortcutStore
import com.homecursor.client.storage.SharedPrefsTrackpadSettingsStore
import com.homecursor.client.storage.SharedPrefsTutorialStore
import com.homecursor.client.ui.HomeCursorApp
import com.homecursor.client.ui.HomeViewModel
import com.homecursor.client.ui.KeyboardViewModel
import com.homecursor.client.ui.ShortcutsViewModel
import com.homecursor.client.ui.TrackpadViewModel
import com.homecursor.client.ui.TutorialViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels { HomeViewModelFactory(applicationContext) }
    private val trackpadViewModel: TrackpadViewModel by viewModels {
        TrackpadViewModelFactory(applicationContext, viewModel.inputSender)
    }

    private val keyboardViewModel: KeyboardViewModel by viewModels {
        KeyboardViewModelFactory(viewModel.inputSender)
    }
    private val shortcutsViewModel: ShortcutsViewModel by viewModels {
        ShortcutsViewModelFactory(applicationContext, viewModel.inputSender)
    }

    private val tutorialViewModel: TutorialViewModel by viewModels { TutorialViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The on-screen keyboard is landscape: turn the phone while it is open, and let it turn back when it closes.
        lifecycleScope.launch {
            keyboardViewModel.isOpen.collect { open ->
                requestedOrientation = if (open) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
        setContent {
            HomeCursorApp(viewModel, trackpadViewModel, keyboardViewModel, shortcutsViewModel, tutorialViewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // A rotation (or other configuration change) also stops the Activity, but the app is still in use.
        if (!isChangingConfigurations) {
            // First let go of held buttons and keys, while the connection that holds them is still open.
            trackpadViewModel.onBackground()
            keyboardViewModel.onBackground()
            viewModel.onBackground()
        }
    }
}

private class TrackpadViewModelFactory(
    private val context: Context,
    private val inputSender: StateFlow<InputSender>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val store = SharedPrefsTrackpadSettingsStore(
            context.getSharedPreferences(SharedPrefsTrackpadSettingsStore.FILE_NAME, Context.MODE_PRIVATE),
        )
        val tuning = TrackpadTuning.forDensity(context.resources.displayMetrics.density)
        return TrackpadViewModel(inputSender, store, tuning) as T
    }
}

private class KeyboardViewModelFactory(private val inputSender: StateFlow<InputSender>) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = KeyboardViewModel(inputSender) as T
}

private class ShortcutsViewModelFactory(
    private val context: Context,
    private val inputSender: StateFlow<InputSender>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val store = SharedPrefsShortcutStore(
            context.getSharedPreferences(SharedPrefsShortcutStore.FILE_NAME, Context.MODE_PRIVATE),
        )
        return ShortcutsViewModel(inputSender, store) as T
    }
}

private class TutorialViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val store = SharedPrefsTutorialStore(
            context.getSharedPreferences(SharedPrefsTutorialStore.FILE_NAME, Context.MODE_PRIVATE),
        )
        return TutorialViewModel(store) as T
    }
}

private class HomeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val discovery = UdpHostDiscovery(NetworkInterfaceBroadcastTargets())
        val connector = TcpHostConnector(clientName = Build.MODEL ?: "Android")
        val store = SharedPrefsSavedHostStore(
            context.getSharedPreferences(SharedPrefsSavedHostStore.FILE_NAME, Context.MODE_PRIVATE),
        )
        return HomeViewModel(discovery) { scope -> ConnectionCoordinator(connector, discovery, store, scope) } as T
    }
}
