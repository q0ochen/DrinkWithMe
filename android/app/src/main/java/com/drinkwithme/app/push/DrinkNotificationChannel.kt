package com.drinkwithme.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Benachrichtigungskanal „drinks“ – die Cloud Function sendet genau an diesen Kanal. */
object DrinkNotificationChannel {
    const val ID = "drinks"

    /** Legt den Kanal an (mehrfacher Aufruf ist unschädlich). minSdk 26 – Kanäle gibt es immer. */
    fun create(context: Context) {
        val channel = NotificationChannel(ID, "Getränke deiner Freunde", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Benachrichtigt dich, wenn jemand aus deinen Trinkrunden etwas trinkt."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
