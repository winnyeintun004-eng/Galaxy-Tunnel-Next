package com.galaxy.tunnel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev7dev.v2ray.V2rayController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _vpnState = MutableStateFlow("disconnected")
    val vpnState: StateFlow<String> = _vpnState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _servers = MutableStateFlow<List<VpnNode>>(emptyList())
    val servers: StateFlow<List<VpnNode>> = _servers

    private val _selectedServerIndex = MutableStateFlow(0)
    val selectedServerIndex: StateFlow<Int> = _selectedServerIndex

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds

    private val _dlSpeed = MutableStateFlow("0.0")
    val dlSpeedMb: StateFlow<String> = _dlSpeed

    private val _ulSpeed = MutableStateFlow("0.0")
    val ulSpeedMb: StateFlow<String> = _ulSpeed

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _currentScreen = MutableStateFlow("servers")
    val currentScreen: StateFlow<String> = _currentScreen

    private val _coreVersion = MutableStateFlow("")
    val coreVersion: StateFlow<String> = _coreVersion

    init {
        _coreVersion.value = V2rayController.getCoreVersion()
        loadDefaultServers()
        startStatsPolling()
    }

    private fun loadDefaultServers() {
        _servers.value = listOf(
            VpnNode(
                "Singapore Premium",
                "Singapore",
                "Ultra fast SG server",
                "vless://uuid@sg.example.com:443?security=tls&sni=sg.example.com&type=ws&path=/ws#SG",
                24
            ),
            VpnNode(
                "Japan Standard",
                "Tokyo, Japan",
                "Reliable JP server",
                "vless://uuid@jp.example.com:443?security=tls&sni=jp.example.com&type=ws&path=/ws#JP",
                45
            ),
            VpnNode(
                "USA West",
                "Los Angeles, USA",
                "High bandwidth US server",
                "vless://uuid@us.example.com:443?security=tls&sni=us.example.com&type=ws&path=/ws#US",
                120
            )
        )
    }

    fun selectServer(index: Int) { _selectedServerIndex.value = index }

    fun updateStatus(status: VpnStatus) {
        _vpnState.value = when (status) {
            VpnStatus.CONNECTED -> "connected"
            VpnStatus.CONNECTING -> "connecting"
            VpnStatus.DISCONNECTED -> "disconnected"
        }
        if (status == VpnStatus.CONNECTED) _durationSeconds.value = 0
    }

    fun updateStatusFromV2Ray(state: V2rayController.ConnectionState) {
        _vpnState.value = when (state) {
            V2rayController.ConnectionState.CONNECTED -> "connected"
            V2rayController.ConnectionState.CONNECTING -> "connecting"
            V2rayController.ConnectionState.DISCONNECTED -> "disconnected"
        }
    }

    fun toggleDarkMode() { _isDarkMode.value = !_isDarkMode.value }

    fun setCurrentScreen(screen: String) { _currentScreen.value = screen }

    fun fetchServers() {
        viewModelScope.launch {
            _isLoading.value = true
            delay(2000)
            _isLoading.value = false
        }
    }

    private fun startStatsPolling() {
        viewModelScope.launch {
            while (isActive) {
                if (_vpnState.value == "connected") {
                    _durationSeconds.value++
                    _dlSpeed.value = String.format("%.1f", kotlin.random.Random.nextDouble(0.5, 50.0))
                    _ulSpeed.value = String.format("%.1f", kotlin.random.Random.nextDouble(0.1, 20.0))
                }
                delay(1000)
            }
        }
    }

    fun addServerFromUri(uri: String) {
        val config = V2RayParser.convertUrlToJson(uri)
        if (config.isNotEmpty()) {
            val remark = uri.substringAfterLast("#", "Custom Server")
            val newNode = VpnNode(remark, "Custom", "Added manually", uri, 0)
            _servers.value = _servers.value + newNode
        }
    }
}

enum class VpnStatus {
    CONNECTED, CONNECTING, DISCONNECTED
}
