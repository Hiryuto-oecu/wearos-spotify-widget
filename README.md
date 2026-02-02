# Wear OS Spotify Widget

> [!NOTE]
> [日本語のドキュメントはこちら](README-ja.md)

> [!WARNING]
> This application is currently under development and may be unstable.

![Demo Image](demo_image.png)

A lightweight Wear OS application and Tile that provides "Now Playing" information and playback controls for Spotify (and other media apps) directly from your wrist.

## Features
- **Wear OS Tile Support**: Access controls quickly by swiping on your watch face.
- **Real-time Updates**: The Tile updates instantly when track or playback state changes (using `MediaController.Callback`).
- **Dynamic Background**: The background changes to the current track's album art.
- **Media Controls**: Play/Pause, Skip Next, and Skip Previous functionality.
- **Universal compatibility**: Works with any music player that properly implements Android's `MediaSession` (Spotify, YouTube Music, etc.).

## Requirements
- Wear OS Device (Targeting API 34 / Wear OS 5).
- Start the app at least once to grant the **Notification Access** permission.
    - *Note: Android requires Notification Access to read MediaMetadata from other apps.*

## Setup

### Granting Permissions (Important)
On many Wear OS devices and emulators, the "Notification Access" setting screen is hidden or inaccessible. **You must grant this permission via ADB for the app to work.**

Run the following command after installing the app:
```bash
adb shell cmd notification allow_listener com.github.hiryuto_oecu.wearos_spotify_widget/.SpotifyWidgetService
```

### Manual Setup (If menu is available)
1. **Install** the app on your Wear OS device.
2. **Open** the app. You will be prompted to grant "Notification Access".
3. **Enable** the permission for "Spotify Widget" in the system settings scan.
4. **Add the Tile**:
    - Swipe to the end of your Tiles.
    - Tap "Add Tile".
    - Select "Spotify Widget".

## Development Info
- **Language**: Kotlin
- **UI Toolkit**: ProtoLayout (Tiles) & Compose for Wear OS (Main Activity)
- **Architecture**:
    - `MainActivity`: Standard app interface with permission handling.
    - `SpotifyTileService`: The core Tile implementation. Uses event-driven architecture to minimize battery usage while ensuring responsiveness.
