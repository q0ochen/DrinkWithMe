package com.drinkwithme.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Benachrichtigungskanal „drinks“ für die Push-Nachrichten der Trinkrunden (siehe docs/TRINKRUNDEN_ARCHITEKTUR.md). */
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
