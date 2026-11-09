package com.drinkwithme.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drinkwithme.app.MainActivity
import com.drinkwithme.app.R

/**
 * Zeigt Benachrichtigungen im Kanal [DrinkNotificationChannel] an; Antippen öffnet den Feed der Runde
 * (unveränderlicher PendingIntent). Ob die Nachricht überhaupt angezeigt werden darf, entscheidet vorher
 * der [PushMessageHandler] (passende `recipientId`).
 */
class AndroidFriendDrinkNotifier(private val context: Context) : FriendDrinkNotifier {

    override fun show(notification: FriendDrinkNotification) {
        if (!canPostNotifications()) return

        val openApp = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            notification.groupId?.let { putExtra(PushPayload.KEY_GROUP_ID, it) }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notification.groupId.hashCode(),
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val built = NotificationCompat.Builder(context, DrinkNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notification.tag, NOTIFICATION_ID, built)
        } catch (error: SecurityException) {
            Log.w(TAG, "Benachrichtigung konnte nicht angezeigt werden.", error)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "DrinkWithMe"

        /** Zusammen mit dem Tag („{authorId}_{drinkId}“) eindeutig – dasselbe Getränk aus mehreren Runden ersetzt sich. */
        const val NOTIFICATION_ID = 1
    }
}
