package com.galaxy.tunnel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dev7dev.v2ray.V2rayController
import com.galaxy.tunnel.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startV2RayConnection()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
            viewModel.updateStatus(VpnStatus.DISCONNECTED)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize V2rayController
        V2rayController.init(this, R.drawable.ic_launcher_foreground, "GalaxyTunnel")

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            var showSplash by remember { mutableStateOf(true) }
            var isNetworkAvailable by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                isNetworkAvailable = isInternetAvailable(this@MainActivity)
                if (!isNetworkAvailable) {
                    Toast.makeText(this@MainActivity, "No Internet!", Toast.LENGTH_LONG).show()
                }
                delay(2500)
                showSplash = false
            }

            // Poll V2Ray status
            LaunchedEffect(Unit) {
                while (true) {
                    val state = V2rayController.getConnectionState()
                    viewModel.updateStatusFromV2Ray(state)
                    delay(1000)
                }
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        GalaxyTunnelApp(
                            viewModel = viewModel,
                            onConnectClick = {
                                val state = viewModel.vpnState.value
                                if (state == "connected") {
                                    stopV2RayConnection()
                                } else {
                                    checkVpnPermissionAndStart()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun checkVpnPermissionAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            startV2RayConnection()
        }
    }

    private fun startV2RayConnection() {
        val index = viewModel.selectedServerIndex.value
        val list = viewModel.servers.value
        val node = list.getOrNull(index)

        if (node == null || node.vlessConfig.isEmpty()) {
            Toast.makeText(this, "No server selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse VLESS URI to JSON config
        val jsonConfig = V2RayParser.convertUrlToJson(node.vlessConfig)
        if (jsonConfig.isEmpty()) {
            Toast.makeText(this, "Invalid config format", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.updateStatus(VpnStatus.CONNECTING)

        // Start V2Ray using dev7dev controller
        V2rayController.startV2ray(this, node.name, jsonConfig, null)
    }

    private fun stopV2RayConnection() {
        V2rayController.stopV2ray(this)
        viewModel.updateStatus(VpnStatus.DISCONNECTED)
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "galaxy")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )

            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer(rotationZ = rotation),
                tint = Color(0xFF00D4FF)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "GALAXY TUNNEL",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Secure. Fast. Limitless.",
                fontSize = 14.sp,
                color = Color(0xFF00D4FF).copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Initializing...",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalaxyTunnelApp(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                DrawerContent(
                    viewModel = viewModel,
                    onItemClick = { screen ->
                        viewModel.setCurrentScreen(screen)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "GALAXY TUNNEL",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = if (isDark) Color.White else Color.Black
                            )
                            Text(
                                text = "Secure. Fast. Limitless.",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.fetchServers() }) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Refresh")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    "servers" -> MainConnectionScreen(viewModel, onConnectClick)
                    "settings" -> SettingsScreen(viewModel)
                    "contact" -> ContactScreen()
                    else -> MainConnectionScreen(viewModel, onConnectClick)
                }
            }
        }
    }
}

@Composable
fun MainConnectionScreen(viewModel: MainViewModel, onConnectClick: () -> Unit) {
    val vpnState by viewModel.vpnState.collectAsState()
    val duration by viewModel.durationSeconds.collectAsState()
    val dlSpeed by viewModel.dlSpeedMb.collectAsState()
    val ulSpeed by viewModel.ulSpeedMb.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val selectedIndex by viewModel.selectedServerIndex.collectAsState()

    var isListExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Connection Button
        Box(contentAlignment = Alignment.Center) {
            if (vpnState == "connected") {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "scale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "alpha"
                )
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
            }

            Surface(
                onClick = { onConnectClick() },
                modifier = Modifier
                    .size(180.dp)
                    .shadow(12.dp, CircleShape),
                shape = CircleShape,
                color = if (vpnState == "connected")
                    Color(0xFF1B5E20)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (vpnState == "connected")
                            Icons.Default.Shield
                        else
                            Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = if (vpnState == "connected")
                            Color(0xFF4CAF50)
                        else
                            Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status Text
        Text(
            text = when (vpnState) {
                "connected" -> "CONNECTED"
                "connecting" -> "CONNECTING..."
                else -> "DISCONNECTED"
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = when (vpnState) {
                "connected" -> Color(0xFF4CAF50)
                "connecting" -> Color(0xFFFF9800)
                else -> Color.Gray
            }
        )

        // Duration
        if (vpnState == "connected") {
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            Text(
                text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Speed Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SpeedItem(
                label = "DOWNLOAD",
                speed = dlSpeed,
                icon = Icons.Default.ArrowDownward,
                color = Color(0xFF00BCD4)
            )
            SpeedItem(
                label = "UPLOAD",
                speed = ulSpeed,
                icon = Icons.Default.ArrowUpward,
                color = Color(0xFFFFC107)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Server Selector
        val currentServer = servers.getOrNull(selectedIndex)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isListExpanded = !isListExpanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentServer?.name ?: "Select Server",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentServer?.location ?: "Unknown",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Icon(
                    if (isListExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }

        AnimatedVisibility(
            visible = isListExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                servers.forEachIndexed { index, node ->
                    ServerListItem(
                        node = node,
                        isSelected = index == selectedIndex,
                        onClick = {
                            viewModel.selectServer(index)
                            isListExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Core Version
        Text(
            text = viewModel.coreVersion.value,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun SpeedItem(label: String, speed: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.Gray
            )
        }
        Text(
            text = "$speed MB/s",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun ServerListItem(node: VpnNode, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else
                    Color.Transparent
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = node.description,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
        Text(
            text = "${node.ping}ms",
            fontSize = 12.sp,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DrawerContent(viewModel: MainViewModel, onItemClick: (String) -> Unit) {
    val isDark by viewModel.isDarkMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.CloudQueue,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = Color.White
                )
                Text(
                    "GALAXY TUNNEL",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
                Text(
                    "Next Gen Proxy Client",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        NavigationDrawerItem(
            label = { Text("Servers") },
            selected = false,
            onClick = { onItemClick("servers") },
            icon = { Icon(Icons.Default.Dns, contentDescription = null) }
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = { onItemClick("settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) }
        )
        NavigationDrawerItem(
            label = { Text("Contact") },
            selected = false,
            onClick = { onItemClick("contact") },
            icon = { Icon(Icons.Default.Email, contentDescription = null) }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Dark mode toggle
        NavigationDrawerItem(
            label = { Text(if (isDark) "Light Mode" else "Dark Mode") },
            selected = false,
            onClick = { viewModel.toggleDarkMode() },
            icon = {
                Icon(
                    if (isDark) Icons.Default.CloudQueue else Icons.Default.CloudQueue,
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "App Version: 1.0.0 (Beta)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Core: ${viewModel.coreVersion.value}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ContactScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Contact Us",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Telegram: @GalaxyTunnelSupport",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Email: support@galaxytunnel.app",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
