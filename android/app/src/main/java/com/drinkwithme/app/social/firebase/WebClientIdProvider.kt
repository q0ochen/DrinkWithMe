package com.drinkwithme.app.social.firebase

import android.annotation.SuppressLint
import android.content.Context

/** Liefert die OAuth-Web-Client-ID für die Google-Anmeldung (oder `null`, wenn sie fehlt). */
fun interface WebClientIdProvider {
    fun webClientId(): String?
}

/**
 * Liest die Ressource `default_web_client_id`, die das google-services-Plugin aus
 * google-services.json erzeugt. Ohne diese Datei gibt es die Ressource nicht – deshalb
 * wird sie zur Laufzeit nachgeschlagen statt über `R.string` referenziert.
 */
class ResourceWebClientIdProvider(private val context: Context) : WebClientIdProvider {

    @SuppressLint("DiscouragedApi")
    override fun webClientId(): String? {
        val resourceId = context.resources.getIdentifier(RESOURCE_NAME, "string", context.packageName)
        if (resourceId == 0) return null
        return context.getString(resourceId).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val RESOURCE_NAME = "default_web_client_id"
    }
}
