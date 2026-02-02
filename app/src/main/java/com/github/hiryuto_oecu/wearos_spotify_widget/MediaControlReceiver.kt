package com.github.hiryuto_oecu.wearos_spotify_widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.view.KeyEvent
import androidx.wear.tiles.TileService

class MediaControlReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PLAY_PAUSE = "com.github.hiryuto_oecu.wearos_spotify_widget.ACTION_PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.github.hiryuto_oecu.wearos_spotify_widget.ACTION_SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.github.hiryuto_oecu.wearos_spotify_widget.ACTION_SKIP_PREVIOUS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        // メディア操作の実行
        val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(context, SpotifyWidgetService::class.java)
        
        try {
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            val controller = controllers.find { it.packageName.contains("spotify", ignoreCase = true) }
                ?: controllers.firstOrNull()

            controller?.let {
                when (action) {
                    ACTION_PLAY_PAUSE -> {
                        // Play/Pause toggle logic might be better handled by checking state, 
                        // but simple dispatch is safer for stateless receiver? 
                        // Actually, getting state here is fine.
                        val state = it.playbackState?.state
                        if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                            it.transportControls.pause()
                        } else {
                            it.transportControls.play()
                        }
                    }
                    ACTION_SKIP_NEXT -> it.transportControls.skipToNext()
                    ACTION_SKIP_PREVIOUS -> it.transportControls.skipToPrevious()
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        // Tileの更新リクエスト
        TileService.getUpdater(context)
            .requestUpdate(SpotifyTileService::class.java)
    }
}
