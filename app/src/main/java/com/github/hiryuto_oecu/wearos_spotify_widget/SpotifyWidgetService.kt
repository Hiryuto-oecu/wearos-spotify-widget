package com.github.hiryuto_oecu.wearos_spotify_widget

import android.service.notification.NotificationListenerService

class SpotifyWidgetService : NotificationListenerService() {
    // MediaSessionへのアクセス権限を得るために存在するだけでよい
    // 必要に応じて通知監視ロジックを追加可能
}
