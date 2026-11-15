package com.homecursor.client

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.homecursor.client.connection.TcpHostConnector
import com.homecursor.client.discovery.NetworkInterfaceBroadcastTargets
import com.homecursor.client.discovery.UdpHostDiscovery
import com.homecursor.client.input.InputSender
import com.homecursor.client.input.TrackpadTuning
import com.homecursor.client.session.ConnectionCoordinator
import com.homecursor.client.storage.SharedPrefsSavedHostStore
import com.homecursor.client.storage.SharedPrefsTrackpadSettingsStore
import com.homecursor.client.ui.HomeCursorApp
import com.homecursor.client.ui.HomeViewModel
import com.homecursor.client.ui.TrackpadViewModel
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels { HomeViewModelFactory(applicationContext) }
    private val trackpadViewModel: TrackpadViewModel by viewModels {
        TrackpadViewModelFactory(applicationContext, viewModel.inputSender)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeCursorApp(viewModel, trackpadViewModel)
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
            // First let go of held buttons, while the connection that holds them is still open.
            trackpadViewModel.onBackground()
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
