package com.github.hiryuto_oecu.wearos_spotify_widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.github.hiryuto_oecu.wearos_spotify_widget.presentation.theme.WearOSSpotifyWidgetTheme
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearOSSpotifyWidgetTheme {
                MainScreen(context = this)
            }
        }
    }
}

@Composable
fun MainScreen(context: Context) {
    var hasPermission by remember { mutableStateOf(checkNotificationPermission(context)) }
    var currentTitle by remember { mutableStateOf("No Media") }
    var currentArtist by remember { mutableStateOf("") }
    var currentArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var activeController by remember { mutableStateOf<MediaController?>(null) }

    // MediaSessionの監視
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(context, SpotifyWidgetService::class.java)

            while (true) {
                try {
                    val controllers = mediaSessionManager.getActiveSessions(componentName)
                    // Spotifyを探す、なければ最初のコントローラを使う
                    val controller = controllers.find { it.packageName.contains("spotify", ignoreCase = true) }
                        ?: controllers.firstOrNull()

                    activeController = controller

                    if (controller != null) {
                        val metadata = controller.metadata
                        val playbackState = controller.playbackState
                        
                        currentTitle = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
                        currentArtist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        currentArtBitmap = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                            ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                        
                        isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
                    } else {
                        currentTitle = "No Active Media"
                        currentArtist = ""
                        currentArtBitmap = null
                        isPlaying = false
                    }
                } catch (e: SecurityException) {
                    // 権限が剥奪された場合など
                    hasPermission = false
                }
                delay(1000) // ポーリング間隔
            }
        }
    }
    
    // Resume時に権限再チェック
    LaunchedEffect(Unit) {
        while(true) {
            hasPermission = checkNotificationPermission(context)
            delay(2000)
        }
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            if (currentArtBitmap != null) {
                Image(
                    bitmap = currentArtBitmap!!.asImageBitmap(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay for readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!hasPermission) {
                    Text(
                        text = "Permission Required",
                        textAlign = TextAlign.Center,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            android.widget.Toast.makeText(context, "設定画面を開けませんでした", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Grant Access")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "If button fails, run this via ADB:",
                        style = MaterialTheme.typography.caption3,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Text(
                        text = "adb shell cmd notification allow_listener com.github.hiryuto_oecu.wearos_spotify_widget/.SpotifyWidgetService",
                        style = MaterialTheme.typography.caption3,
                        textAlign = TextAlign.Center,
                        color = Color.LightGray, // 視認性向上のため色変更
                        modifier = Modifier.size(width = 180.dp, height = 100.dp)
                    )
                } else {
                    // Title
                    Text(
                        text = currentTitle,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.title3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    // Artist
                    if (currentArtist.isNotEmpty()) {
                        Text(
                            text = currentArtist,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.body2,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Controls
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous
                        Button(
                            onClick = { activeController?.transportControls?.skipToPrevious() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous"
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Play/Pause
                        Button(
                            onClick = { 
                                if (isPlaying) {
                                    activeController?.transportControls?.pause()
                                } else {
                                    activeController?.transportControls?.play()
                                }
                            },
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Next
                        Button(
                            onClick = { activeController?.transportControls?.skipToNext() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

fun checkNotificationPermission(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val packageName = context.packageName
    return enabledListeners != null && enabledListeners.contains(packageName)
}
