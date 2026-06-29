package com.galaxy.tunnel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galaxy.tunnel.fmt.V2RayConfigParser
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VpnStatus {
    DISCONNECTED, CONNECTING, CONNECTED
}

data class VpnNode(
    val id: Int,
    val name: String,
    val description: String,
    val location: String,
    val vlessConfig: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // App Preferences
    private val _language = MutableStateFlow("my")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _currentScreen = MutableStateFlow("servers")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // VPN States
    private val _vpnState = MutableStateFlow("disconnected")
    val vpnState: StateFlow<String> = _vpnState.asStateFlow()

    private val _vpnStatus = MutableStateFlow(VpnStatus.DISCONNECTED)
    val vpnStatus: StateFlow<VpnStatus> = _vpnStatus.asStateFlow()

    private val _selectedServerIndex = MutableStateFlow(0)
    val selectedServerIndex: StateFlow<Int> = _selectedServerIndex.asStateFlow()

    // Metrics
    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

    private val _dlSpeedMb = MutableStateFlow("0.0")
    val dlSpeedMb: StateFlow<String> = _dlSpeedMb.asStateFlow()

    private val _ulSpeedMb = MutableStateFlow("0.0")
    val ulSpeedMb: StateFlow<String> = _ulSpeedMb.asStateFlow()

    // Servers
    private val _servers = MutableStateFlow<List<VpnNode>>(emptyList())
    val servers: StateFlow<List<VpnNode>> = _servers.asStateFlow()

    private var metricsJob: Job? = null

    // GitHub Raw URL (User can change this in the future)
    private val DEFAULT_SUB_URL = "https://raw.githubusercontent.com/yourusername/yourrepo/main/servers.txt"

    init {
        // Default local server
        _servers.value = listOf(
            VpnNode(1, "Default Node", "Local Config", "Singapore", "vless://...")
        )
    }

    fun updateStatus(status: VpnStatus) {
        _vpnStatus.value = status
        _vpnState.value = if (status == VpnStatus.CONNECTED) "connected" else "disconnected"
        if (status == VpnStatus.CONNECTED) startMetrics() else stopMetrics()
        triggerVibration()
    }

    fun setLanguage(lang: String) { _language.value = lang }
    fun setCurrentScreen(screen: String) { _currentScreen.value = screen }
    fun selectServer(index: Int) { if (index in _servers.value.indices) _selectedServerIndex.value = index }

    private fun startMetrics() {
        metricsJob?.cancel()
        metricsJob = viewModelScope.launch {
            _durationSeconds.value = 0
            while (_vpnState.value == "connected") {
                delay(1000)
                _durationSeconds.value += 1
                _dlSpeedMb.value = String.format("%.1f", Random.nextDouble(1.5, 25.0))
                _ulSpeedMb.value = String.format("%.1f", Random.nextDouble(0.5, 8.0))
            }
        }
    }

    private fun stopMetrics() {
        metricsJob?.cancel()
        _durationSeconds.value = 0
        _dlSpeedMb.value = "0.0"
        _ulSpeedMb.value = "0.0"
    }

    private fun triggerVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {}
    }

    /**
     * Fetch servers from GitHub Raw URL or Subscription URL
     */
    fun fetchServers(url: String = DEFAULT_SUB_URL) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fetchedConfigs = withContext(Dispatchers.IO) {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    // Handle both Base64 and Raw Text (GitHub usually uses Raw Text)
                    val content = try {
                        String(Base64.decode(responseText.trim(), Base64.DEFAULT))
                    } catch (e: Exception) {
                        responseText
                    }
                    
                    content.lines()
                        .filter { it.isNotBlank() && (it.startsWith("vless://") || it.startsWith("trojan://") || it.startsWith("vmess://")) }
                        .mapNotNull { configUrl ->
                            val parsed = V2RayConfigParser.parseUrl(configUrl)
                            if (parsed != null) {
                                VpnNode(
                                    id = Random.nextInt(),
                                    name = parsed["remark"] ?: "GitHub Server",
                                    location = "Remote",
                                    description = parsed["protocol"]?.uppercase() ?: "VPN",
                                    vlessConfig = configUrl
                                )
                            } else null
                        }
                }
                
                if (fetchedConfigs.isNotEmpty()) {
                    _servers.value = fetchedConfigs
                    _selectedServerIndex.value = 0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
