package com.example.monitor

import android.os.Bundle
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

const val AGORA_APP_ID = "40b0b12b81794659ac17a9a66fab985a"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonitorAppTheme {
                MonitorAppNav()
            }
        }
    }
}

@Composable
fun MonitorAppNav() {
    var currentScreen by remember { mutableStateOf("list") }
    var selectedChannelName by remember { mutableStateOf("") }

    if (currentScreen == "list") {
        ActiveUserListScreen(onUserSelected = { channelName ->
            selectedChannelName = channelName
            currentScreen = "video"
        })
    } else {
        LiveStreamViewer(channelName = selectedChannelName, onBack = { currentScreen = "list" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveUserListScreen(onUserSelected: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var cameras by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            db.collection("active_cameras").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    cameras = snapshot.documents.map { doc ->
                        mapOf("email" to (doc.getString("email") ?: "Unknown"), "channel" to doc.id)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Monitor Dashboard") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Active Cameras", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
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

@Composable
fun LiveStreamViewer(channelName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }
    val db = FirebaseFirestore.getInstance()

    // IP Address State (Loaded from DB)
    var fileServerUrl by remember { mutableStateOf("Fetching IP...") }

    val remoteSurfaceView = remember { SurfaceView(context).apply { setZOrderMediaOverlay(true) } }

    // 1. Listen for IP Address in Database
    LaunchedEffect(channelName) {
        db.collection("active_cameras").document(channelName)
            .addSnapshotListener { snapshot, _ ->
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

        // --- TOP BAR: SHOWS LAPTOP LINK ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(top = 40.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Laptop File Link:", color = Color.LightGray, fontSize = 12.sp)
            Text(
                text = fileServerUrl,
                color = Color.Green,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Switch Camera Button
        IconButton(
            onClick = {
                db.collection("commands").document(channelName).set(mapOf("action" to "switch_camera"))
                Toast.makeText(context, "Switching...", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Switch Camera", tint = Color.White)
        }
    }
}

@Composable
fun MonitorAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}