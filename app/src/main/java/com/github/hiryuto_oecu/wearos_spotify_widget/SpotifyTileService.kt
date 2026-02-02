package com.github.hiryuto_oecu.wearos_spotify_widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.wear.tiles.EventBuilders
import java.io.ByteArrayOutputStream

class SpotifyTileService : TileService() {
    
    // Actions
    private val ID_PREVIOUS = "id_prev"
    private val ID_PLAY_PAUSE = "id_play_pause"
    private val ID_NEXT = "id_next"
    
    // Resource IDs
    private val ID_IMAGE_ALBUM_ART = "image_album_art"
    private val ID_ICON_PLAY = "icon_play"
    private val ID_ICON_PAUSE = "icon_pause"
    private val ID_ICON_PREVIOUS = "icon_previous"
    private val ID_ICON_NEXT = "icon_next"

    // Callback Management
    private var activeController: android.media.session.MediaController? = null
    
    private val controllerCallback = object : android.media.session.MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            requestUpdate()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            requestUpdate()
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers)
    }
    
    
    // Lifecycle methods for Tile
    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(requestParams)
        val mediaManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, SpotifyWidgetService::class.java)
        
        try {
            mediaManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
            updateActiveController(mediaManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            // Permission might be revoked
        }
    }

    override fun onTileLeaveEvent(requestParams: EventBuilders.TileLeaveEvent) {
        super.onTileLeaveEvent(requestParams)
        val mediaManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaManager.removeOnActiveSessionsChangedListener(sessionListener)
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
    }

    private fun updateActiveController(controllers: List<android.media.session.MediaController>?) {
        activeController?.unregisterCallback(controllerCallback)
        
        val controller = controllers?.find { it.packageName.contains("spotify", ignoreCase = true) }
            ?: controllers?.firstOrNull()
            
        activeController = controller
        controller?.registerCallback(controllerCallback)
        requestUpdate()
    }

    private fun requestUpdate() {
        androidx.wear.tiles.TileService.getUpdater(this)
            .requestUpdate(SpotifyTileService::class.java)
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val context = this
        val mediaManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(context, SpotifyWidgetService::class.java)

        // Handle Clicks if any
        if (requestParams.state != null && requestParams.state!!.lastClickableId.isNotEmpty()) {
            val clickedId = requestParams.state!!.lastClickableId
            handleMediaAction(mediaManager, componentName, clickedId)
        }

        var title = "No Media"
        var artist = ""
        var isPlaying = false
        var hasArt = false

        try {
            val controllers = mediaManager.getActiveSessions(componentName)
            val controller = controllers.find { it.packageName.contains("spotify", ignoreCase = true) }
                ?: controllers.firstOrNull()

            if (controller != null) {
                val metadata = controller.metadata
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                
                val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                hasArt = art != null
            }
        } catch (e: Exception) {
            title = "Permission Required"
        }

        // Generate a simpler version string based on content to force updates when track changes
        val version = "${title.hashCode()}_${artist.hashCode()}_${isPlaying}"

        val singleTileTimeline = TimelineBuilders.Timeline.fromLayoutElement(
            buildLayout(requestParams.deviceConfiguration, title, artist, isPlaying, hasArt)
        )

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(version)
            .setTileTimeline(singleTileTimeline)
            // No strict freshness needed as we update on event, but keep a fallback
            .setFreshnessIntervalMillis(500) 
            .build()

        return Futures.immediateFuture(tile)
    }
    
    private fun handleMediaAction(mediaManager: MediaSessionManager, componentName: ComponentName, actionId: String) {
        try {
            val controllers = mediaManager.getActiveSessions(componentName)
            val controller = controllers.find { it.packageName.contains("spotify", ignoreCase = true) }
                ?: controllers.firstOrNull()

            controller?.let {
                when (actionId) {
                    ID_PLAY_PAUSE -> {
                        val state = it.playbackState?.state
                        if (state == PlaybackState.STATE_PLAYING) {
                            it.transportControls.pause()
                        } else {
                            it.transportControls.play()
                        }
                    }
                    ID_NEXT -> it.transportControls.skipToNext()
                    ID_PREVIOUS -> it.transportControls.skipToPrevious()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val checkVersion = requestParams.version
        val context = this
        val mediaManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(context, SpotifyWidgetService::class.java)
        
        val resourcesBuilder = ResourceBuilders.Resources.Builder()
            .setVersion(checkVersion)

        // Add Icon Resources
        resourcesBuilder.addIdToImageMapping(ID_ICON_PLAY, ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder()
                .setResourceId(R.drawable.ic_play_arrow)
                .build())
            .build())
        resourcesBuilder.addIdToImageMapping(ID_ICON_PAUSE, ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder()
                .setResourceId(R.drawable.ic_pause)
                .build())
            .build())
        resourcesBuilder.addIdToImageMapping(ID_ICON_PREVIOUS, ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder()
                .setResourceId(R.drawable.ic_skip_previous)
                .build())
            .build())
        resourcesBuilder.addIdToImageMapping(ID_ICON_NEXT, ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder()
                .setResourceId(R.drawable.ic_skip_next)
                .build())
            .build())

        try {
            val controllers = mediaManager.getActiveSessions(componentName)
            val controller = controllers.find { it.packageName.contains("spotify", ignoreCase = true) }
                ?: controllers.firstOrNull()
            
            val metadata = controller?.metadata
            val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)

            if (art != null) {
                // Resize for Tile limits
                val scaledArt = Bitmap.createScaledBitmap(art, 300, 300, true)
                val stream = ByteArrayOutputStream()
                scaledArt.compress(Bitmap.CompressFormat.PNG, 80, stream)
                val byteArray = stream.toByteArray()
                
                resourcesBuilder.addIdToImageMapping(
                    ID_IMAGE_ALBUM_ART,
                    ResourceBuilders.ImageResource.Builder()
                        .setInlineResource(
                            ResourceBuilders.InlineImageResource.Builder()
                                .setData(byteArray)
                                .setWidthPx(300)
                                .setHeightPx(300)
                                .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                                .build()
                        )
                        .build()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Futures.immediateFuture(resourcesBuilder.build())
    }

    private fun buildLayout(
        deviceParameters: DeviceParameters,
        title: String,
        artist: String,
        isPlaying: Boolean,
        hasArt: Boolean
    ): LayoutElementBuilders.LayoutElement {
        
        val playPauseIconId = if (isPlaying) ID_ICON_PAUSE else ID_ICON_PLAY
        
        // Root Box
        val rootBox = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        // 1. Background Image (if available)
        if (hasArt) {
            rootBox.addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(ID_IMAGE_ALBUM_ART)
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
                    .build()
            )
            
            // 2. Overlay (Black 40%)
            rootBox.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(androidx.wear.protolayout.ColorBuilders.argb(0x66000000)) // 40% alpha black
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        }

        // 3. Content Column
        rootBox.addContent(
            LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.wrap())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    Text.Builder(this, title)
                        .setTypography(Typography.TYPOGRAPHY_TITLE3)
                        .setColor(androidx.wear.protolayout.ColorBuilders.argb(0xFFFFFFFF.toInt()))
                        .setMaxLines(2)
                        .setModifiers(ModifiersBuilders.Modifiers.Builder()
                            .setPadding(ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(16f))
                                .setEnd(DimensionBuilders.dp(16f))
                                .build())
                            .build())
                        .build()
                )
                .addContent(
                    Text.Builder(this, artist)
                        .setTypography(Typography.TYPOGRAPHY_BODY2)
                        .setColor(androidx.wear.protolayout.ColorBuilders.argb(0xFFFFFFFF.toInt())) // White
                        .setMaxLines(1)
                         .setModifiers(ModifiersBuilders.Modifiers.Builder()
                            .setPadding(ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(16f))
                                .setEnd(DimensionBuilders.dp(16f))
                                .build())
                            .build())
                        .build()
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(16f)).build())
                .addContent(
                    LayoutElementBuilders.Row.Builder()
                        .addContent(createButton(ID_ICON_PREVIOUS, ID_PREVIOUS, 40f))
                        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(8f)).build())
                        .addContent(createButton(playPauseIconId, ID_PLAY_PAUSE, 50f))
                        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(8f)).build())
                        .addContent(createButton(ID_ICON_NEXT, ID_NEXT, 40f))
                        .build()
                )
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(24f)).build())
                .build()
        )

        return rootBox.build()
    }

    private fun createButton(iconId: String, actionId: String, sizeDp: Float): LayoutElementBuilders.LayoutElement {
        return Button.Builder(this, 
                ModifiersBuilders.Clickable.Builder()
                    .setId(actionId)
                    .setOnClick(
                        ActionBuilders.LoadAction.Builder().build()
                    )
                    .build()
            )
            .setIconContent(iconId)
            .setSize(DimensionBuilders.dp(sizeDp))
            .build()
    }
}
