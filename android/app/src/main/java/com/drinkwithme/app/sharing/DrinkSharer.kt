package com.drinkwithme.app.sharing

import android.content.Context
import android.content.Intent

/** Gibt einen Text an andere Apps weiter (WhatsApp, Instagram, Messenger …). */
fun interface DrinkSharer {
    fun share(context: Context, text: String)
}

/** Nutzt das Android-Share-Sheet über `Intent.ACTION_SEND`. */
class IntentDrinkSharer : DrinkSharer {
    override fun share(context: Context, text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, "Getränk teilen").apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
