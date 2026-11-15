package com.homecursor.client.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import com.homecursor.client.connection.HostEndpoint

class SharedPrefsSavedHostStore(private val preferences: SharedPreferences) : SavedHostStore {
    override fun load(): HostEndpoint? {
        val address = preferences.getString(KEY_ADDRESS, null) ?: return null
        val name = preferences.getString(KEY_NAME, null) ?: return null
        val tcpPort = preferences.getInt(KEY_TCP_PORT, 0)
        val udpPort = preferences.getInt(KEY_UDP_PORT, 0)
        if (tcpPort == 0) return null
        return HostEndpoint(address, name, tcpPort, udpPort)
    }

    override fun save(host: HostEndpoint) = preferences.edit {
        putString(KEY_ADDRESS, host.address)
        putString(KEY_NAME, host.name)
        putInt(KEY_TCP_PORT, host.tcpPort)
        putInt(KEY_UDP_PORT, host.udpPort)
    }

    override fun clear() = preferences.edit { clear() }

    companion object {
        const val FILE_NAME = "saved_host"
        private const val KEY_ADDRESS = "address"
        private const val KEY_NAME = "name"
        private const val KEY_TCP_PORT = "tcp_port"
        private const val KEY_UDP_PORT = "udp_port"
    }
}
