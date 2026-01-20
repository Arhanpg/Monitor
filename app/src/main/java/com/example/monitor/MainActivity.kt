package com.example.monitor

import android.Manifest
import android.content.Context
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

// --- CONFIGURATION ---
// PASTE YOUR AGORA APP ID HERE!
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
        ActiveUserListScreen(
            onUserSelected = { channelName ->
                selectedChannelName = channelName
                currentScreen = "video"
            }
        )
    } else {
        LiveStreamViewer(
            channelName = selectedChannelName,
            onBack = { currentScreen = "list" }
        )
    }
}

// ===========================
// SCREEN 1: LIST OF CAMERAS
// ===========================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveUserListScreen(onUserSelected: (String) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    var cameras by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            db.collection("active_cameras").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.map { doc ->
                        mapOf(
                            "email" to (doc.getString("email") ?: "Unknown"),
                            "channel" to doc.id
                        )
                    }
                    cameras = list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Monitor Dashboard") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Active Cameras", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            if (cameras.isEmpty()) {
                Text("No active cameras found...", color = Color.Gray)
            }

            LazyColumn {
                items(cameras) { cam ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onUserSelected(cam["channel"]!!) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

// ===========================
// SCREEN 2: LIVE VIDEO VIEWER
// ===========================
@Composable
fun LiveStreamViewer(channelName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }

    val remoteSurfaceView = remember {
        SurfaceView(context).apply {
            setZOrderMediaOverlay(true)
        }
    }

    DisposableEffect(channelName) {
        try {
            val engine = RtcEngine.create(context, AGORA_APP_ID, object : IRtcEngineEventHandler() {
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    super.onUserJoined(uid, elapsed)
                    (context as? ComponentActivity)?.runOnUiThread {
                        val videoCanvas = VideoCanvas(remoteSurfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid)
                        rtcEngine?.setupRemoteVideo(videoCanvas)
                    }
                }
            })

            engine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            engine.setClientRole(Constants.CLIENT_ROLE_AUDIENCE)
            engine.enableVideo()
            engine.joinChannel(null, channelName, null, 0)
            rtcEngine = engine

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        onDispose {
            rtcEngine?.leaveChannel()
            RtcEngine.destroy()
            rtcEngine = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { remoteSurfaceView },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Text(
            text = "Monitoring: $channelName",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        )
    }
}

@Composable
fun MonitorAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}