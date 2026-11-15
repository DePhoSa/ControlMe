package de.controlme.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CH_STATUS = "status"
    const val CH_SESSION = "session"

    const val ID_CLIENT = 1
    const val ID_SHARE = 2
    const val ID_REQUEST = 3
    const val ID_INFO = 4

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Bereitschaft", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Zeigt an, dass Fernhilfe bereit ist"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SESSION, "Fernhilfe", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Anfragen und laufende Fernhilfe"
            }
        )
    }
}
