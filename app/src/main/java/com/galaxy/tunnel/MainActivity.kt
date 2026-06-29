package com.galaxy.tunnel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import android.net.VpnService
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.galaxy.tunnel.ui.theme.MyApplicationTheme
import com.galaxy.tunnel.service.V2RayService

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startV2RayService()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for VPN status", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
                    Toast.makeText(this@MainActivity, "No Internet Connection!", Toast.LENGTH_LONG).show()
                }
                delay(3000) // Splash duration to match the vibe
                showSplash = false
            }

            MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
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
                                if (viewModel.vpnState.value == "connected") {
                                    stopV2RayService()
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
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun checkVpnPermissionAndStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            startV2RayService()
        }
    }

    private fun startV2RayService() {
        val index = viewModel.selectedServerIndex.value
        val list = viewModel.servers.value
        val config = list.getOrNull(index)?.vlessConfig ?: ""
        if (config.isEmpty()) {
            Toast.makeText(this, "No server selected", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, V2RayService::class.java).apply {
            action = V2RayService.ACTION_START
            putExtra("V2RAY_CONFIG", config)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        viewModel.updateStatus(VpnStatus.CONNECTED)
    }

    private fun stopV2RayService() {
        val intent = Intent(this, V2RayService::class.java).apply {
            action = V2RayService.ACTION_STOP
        }
        startService(intent)
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
            // Logo and Title Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = android.R.mipmap.sym_def_app_icon), // Placeholder for ic_launcher
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "GALAXY TUNNEL",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(100.dp))

            // Central Galaxy Animation (to match the link image)
            val infiniteTransition = rememberInfiniteTransition(label = "galaxy")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ), label = "rotation"
            )

            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CloudQueue, // Placeholder for the spiral galaxy icon
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer(rotationZ = rotation),
                    tint = Color(0xFF00D4FF)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Connecting you to the Cosmos...",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "App Version 1.0",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
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
    val language by viewModel.language.collectAsState()
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
                                text = Translation.get("headerTitle", language),
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
                        IconButton(onClick = { 
                            viewModel.fetchServers()
                        }) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Update from GitHub")
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
                    "contact" -> ContactScreen(viewModel)
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
    val language by viewModel.language.collectAsState()
    
    var isListExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            if (vpnState == "connected") {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ), label = "scale"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ), label = "alpha"
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
                color = if (vpnState == "connected") Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (vpnState == "connected") Icons.Default.Shield else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = if (vpnState == "connected") Color(0xFF4CAF50) else Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (vpnState == "connected") "CONNECTED" else if (vpnState == "connecting") "CONNECTING..." else "DISCONNECTED",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (vpnState == "connected") Color(0xFF4CAF50) else if (vpnState == "connecting") Color(0xFFFF9800) else Color.Gray
        )

        if (vpnState == "connected") {
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            Text(
                text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SpeedItem(label = "DOWNLOAD", speed = dlSpeed, icon = Icons.Default.ArrowDownward, color = Color(0xFF00BCD4))
            SpeedItem(label = "UPLOAD", speed = ulSpeed, icon = Icons.Default.ArrowUpward, color = Color(0xFFFFC107))
        }

        Spacer(modifier = Modifier.height(40.dp))

        val currentServer = servers.getOrNull(selectedIndex)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isListExpanded = !isListExpanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = currentServer?.name ?: "Select Server", fontWeight = FontWeight.Bold)
                    Text(text = currentServer?.location ?: "Unknown", fontSize = 12.sp, color = Color.Gray)
                }
                Icon(
                    if (isListExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }

        AnimatedVisibility(visible = isListExpanded) {
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
    }
}

@Composable
fun SpeedItem(label: String, speed: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 10.sp, color = Color.Gray)
        }
        Text(text = "$speed MB/s", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ServerListItem(node: VpnNode, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = node.name, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            Text(text = node.description, fontSize = 11.sp, color = Color.Gray)
        }
        Text(
            text = "24ms",
            fontSize = 12.sp,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DrawerContent(viewModel: MainViewModel, onItemClick: (String) -> Unit) {
    val language by viewModel.language.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(60.dp), tint = Color.White)
                Text("GALAXY TUNNEL", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text("Next Gen Proxy Client", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        NavigationDrawerItem(
            label = { Text(Translation.get("navServers", language)) },
            selected = false,
            onClick = { onItemClick("servers") },
            icon = { Icon(Icons.Default.Dns, contentDescription = null) }
        )
        NavigationDrawerItem(
            label = { Text(Translation.get("navSettings", language)) },
            selected = false,
            onClick = { onItemClick("settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) }
        )
        NavigationDrawerItem(
            label = { Text(Translation.get("navContact", language)) },
            selected = false,
            onClick = { onItemClick("contact") },
            icon = { Icon(Icons.Default.Email, contentDescription = null) }
        )
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Version: 1.0.0 (Beta)", color = Color.Gray)
    }
}

@Composable
fun ContactScreen(viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Contact Us", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Telegram: @GalaxyTunnelSupport", color = MaterialTheme.colorScheme.primary)
    }
}
