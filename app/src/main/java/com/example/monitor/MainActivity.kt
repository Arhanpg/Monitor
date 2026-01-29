package com.example.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Bundle
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

// --- CONSTANTS ---
const val AGORA_APP_ID = "40b0b12b81794659ac17a9a66fab985a"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // --- SELF-CONTAINED THEME (No extra files needed) ---
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFBB86FC),
                    secondary = Color(0xFF03DAC5),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onPrimary = Color.Black,
                    onSecondary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    errorContainer = Color(0xFFCF6679),
                    onErrorContainer = Color.Black
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PermissionHandler {
                        MonitorAppNav()
                    }
                }
            }
        }
    }
}

// --- PERMISSION HANDLER ---
@Composable
fun PermissionHandler(content: @Composable () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        // Add Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcher.launch(permissions.toTypedArray())
    }
    content()
}

// --- NAVIGATION ---
@Composable
fun MonitorAppNav() {
    var currentScreen by remember { mutableStateOf("home") }
    var selectedChannelName by remember { mutableStateOf("") }

    when (currentScreen) {
        "home" -> HomeScreen(
            onGoToMonitor = { currentScreen = "camera_list" },
            onGoToScanner = { currentScreen = "scanner" }
        )
        "camera_list" -> ActiveUserListScreen(
            onUserSelected = { channel ->
                selectedChannelName = channel
                currentScreen = "video"
            },
            onBack = { currentScreen = "home" }
        )
        "video" -> LiveStreamViewer(
            channelName = selectedChannelName,
            onBack = { currentScreen = "camera_list" }
        )
        "scanner" -> PermissionLaunderingScanner(
            onBack = { currentScreen = "home" }
        )
    }
}

// --- HOME SCREEN ---
@Composable
fun HomeScreen(onGoToMonitor: () -> Unit, onGoToScanner: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Monitor Tools", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onGoToMonitor,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text("Live Camera Monitor", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onGoToScanner,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text("Mule Permission Scanner", fontSize = 18.sp)
        }
    }
}

// --- SCANNER LOGIC & UI ---
data class AppAnalysis(
    val appName: String,
    val packageName: String,
    val definedCustomPermissions: List<CustomPermission>
)

data class CustomPermission(
    val name: String,
    val protectionLevel: String,
    val isRisk: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionLaunderingScanner(onBack: () -> Unit) {
    val context = LocalContext.current
    var appList by remember { mutableStateOf<List<AppAnalysis>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        try {
            // Safe call for Android 13+ (Tiramisu) vs Older versions
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }

            val analyzedApps = installedPackages.mapNotNull { packInfo ->
                val appName = packInfo.applicationInfo.loadLabel(pm).toString()
                val packageName = packInfo.packageName

                // Filter: Find apps that DEFINE custom permissions (Potential Mules)
                val definedPerms = packInfo.permissions?.mapNotNull { permInfo ->
                    if (permInfo.name.startsWith("android.permission")) return@mapNotNull null

                    val protLevelInt = permInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
                    val protLevel = when (protLevelInt) {
                        PermissionInfo.PROTECTION_NORMAL -> "Normal (RISK)"
                        PermissionInfo.PROTECTION_DANGEROUS -> "Dangerous (RISK)"
                        PermissionInfo.PROTECTION_SIGNATURE -> "Signature (Safe)"
                        else -> "Other"
                    }
                    // The Paper "Mules" focuses on Normal/Dangerous permissions being abused
                    val isRisk = (protLevelInt == PermissionInfo.PROTECTION_NORMAL)
                    CustomPermission(permInfo.name, protLevel, isRisk)
                } ?: emptyList()

                if (definedPerms.isNotEmpty()) {
                    AppAnalysis(appName, packageName, definedPerms)
                } else {
                    null
                }
            }.sortedByDescending { it.definedCustomPermissions.count { p -> p.isRisk } }

            appList = analyzedApps
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mule Scanner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
                if (appList.isEmpty()) {
                    item { Text("No apps with custom permissions found.\n(Check if QUERY_ALL_PACKAGES is in Manifest)", color = Color.Gray) }
                }
                items(appList) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (app.definedCustomPermissions.any { it.isRisk })
                                Color(0xFF3E2723) // Dark Red background for High Risk
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(app.appName, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)

                            Spacer(modifier = Modifier.height(8.dp))

                            app.definedCustomPermissions.forEach { perm ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "• ${perm.name.substringAfterLast(".")}",
                                        fontSize = 12.sp,
                                        color = Color.LightGray,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        perm.protectionLevel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if(perm.isRisk) Color.Red else Color.Green
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ACTIVE CAMERA LIST (Monitor Feature) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveUserListScreen(onUserSelected: (String) -> Unit, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var cameras by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.collection("active_cameras").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                cameras = snapshot.documents.map { doc ->
                    mapOf("email" to (doc.getString("email") ?: "Unknown"), "channel" to doc.id)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Cameras") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (cameras.isEmpty()) Text("No active cameras found...", color = Color.Gray)
            LazyColumn {
                items(cameras) { cam ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onUserSelected(cam["channel"]!!) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(cam["email"] ?: "Camera", style = MaterialTheme.typography.titleSmall)
                                Text("Tap to view live feed", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- LIVE VIEWER (Monitor Feature) ---
@Composable
fun LiveStreamViewer(channelName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }
    val db = FirebaseFirestore.getInstance()
    var fileServerUrl by remember { mutableStateOf("Fetching IP...") }
    val remoteSurfaceView = remember { SurfaceView(context).apply { setZOrderMediaOverlay(true) } }

    LaunchedEffect(channelName) {
        db.collection("active_cameras").document(channelName).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                fileServerUrl = snapshot.getString("file_url") ?: "No Access"
            }
        }
    }

    DisposableEffect(channelName) {
        val engine = RtcEngine.create(context, AGORA_APP_ID, object : IRtcEngineEventHandler() {
            override fun onUserJoined(uid: Int, elapsed: Int) {
                (context as? ComponentActivity)?.runOnUiThread {
                    val videoCanvas = VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
                    rtcEngine?.setupRemoteVideo(videoCanvas)
                }
            }
        })
        engine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
        engine.setClientRole(Constants.CLIENT_ROLE_AUDIENCE)
        engine.enableVideo()
        engine.enableAudio()
        engine.joinChannel(null, channelName, null, 0)
        rtcEngine = engine

        onDispose {
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { remoteSurfaceView }, modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(top = 40.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Laptop File Link:", color = Color.LightGray, fontSize = 12.sp)
            Text(text = fileServerUrl, color = Color.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        IconButton(onClick = { db.collection("commands").document(channelName).set(mapOf("action" to "switch_camera")) }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Switch Camera", tint = Color.White)
        }
    }
}