package com.drinkwithme.app.push

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.drinkwithme.app.R
import com.drinkwithme.app.routing.IntentRouter

/** Kanal „drinks“ für Getränke von Freunden. */
object DrinkNotificationChannel {
    const val ID = "drinks"

    fun create(context: Context) {
        val channel = NotificationChannel(
            ID,
            context.getString(R.string.notification_channel_drinks_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_drinks_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}

/**
 * Zeigt „Anna trinkt gerade – Apfelschorle (500 ml)“. Antippen öffnet den Ereignis-Bildschirm, die Aktion
 * „Vorlesen“ öffnet ihn mit automatischem Vorlesen. Jedes Ereignis hat eigene RequestCodes.
 * Ohne Benachrichtigungs-Erlaubnis passiert nichts (die App fragt erst, wenn es sinnvoll ist).
 */
class AndroidFriendDrinkNotifier(private val context: Context) : FriendDrinkNotifier {

    // Die Erlaubnis wird über areNotificationsEnabled() geprüft; ein Entzug dazwischen wird abgefangen.
    @SuppressLint("MissingPermission")
    override fun show(content: DrinkNotificationContent) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, DrinkNotificationChannel.ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent(content, autoRead = false, NotificationRequestCodes.open(content.eventId)))
            .addAction(
                0,
                context.getString(R.string.notification_action_read_aloud),
                pendingIntent(content, autoRead = true, NotificationRequestCodes.readAloud(content.eventId)),
            )
            .build()
        try {
            manager.notify(NotificationRequestCodes.open(content.eventId), notification)
        } catch (error: SecurityException) {
            // Erlaubnis wurde zwischen Prüfung und Anzeige entzogen.
        }
    }

    private fun pendingIntent(content: DrinkNotificationContent, autoRead: Boolean, requestCode: Int): PendingIntent {
        // Über den nicht exportierten Einstieg: Nur so glaubt die App Titel, Text und „sofort vorlesen“.
        val intent = Intent().setClassName(context, IntentRouter.NOTIFICATION_ENTRY).apply {
            action = IntentRouter.ACTION_OPEN_EVENT
            putExtra(IntentRouter.EXTRA_EVENT_ID, content.eventId)
            putExtra(IntentRouter.EXTRA_TITLE, content.title)
            putExtra(IntentRouter.EXTRA_BODY, content.body)
            putExtra(IntentRouter.EXTRA_AUTO_READ, autoRead)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
