package com.drinkwithme.app.push

import android.content.Context
import android.content.pm.PackageManager
import org.unifiedpush.android.connector.UnifiedPush

/** Ein installierter UnifiedPush-Distributor (z. B. ntfy). */
data class DistributorInfo(val packageName: String, val label: String) {
    val isNtfy: Boolean get() = packageName == PushTexts.NTFY_PACKAGE
}

/** Verwaltung der Push-Distributoren (Einrichtungs-Assistent in den Einstellungen). */
interface PushDistributors {
    /** Installierte Distributoren, ntfy zuerst. */
    fun installed(): List<DistributorInfo>

    /** Der gewählte Distributor oder `null`. */
    fun selected(): String?

    /** Wählt [packageName] und registriert die App dort (mit dem öffentlichen VAPID-Schlüssel). */
    fun use(packageName: String): Boolean

    /** Regelmäßige Neu-Registrierung (empfohlen beim App-Start), falls ein Distributor gewählt ist. */
    fun reRegister()

    /**
     * Neuen Endpunkt anfordern: beim gewählten Distributor ab- und wieder anmelden (neue Registrierung, neue Adresse).
     * Nötig, wenn der alte Endpunkt beim Server noch unter einem anderen Konto liegt. `false` ohne Distributor.
     */
    fun renew(): Boolean

    /** Push ausschalten. */
    fun disable()
}

/** [PushDistributors] über den UnifiedPush-Connector. Ohne VAPID-Schlüssel ist Push nicht möglich. */
class UnifiedPushDistributors(
    private val context: Context,
    private val vapidPublicKey: String,
) : PushDistributors {

    override fun installed(): List<DistributorInfo> = UnifiedPush.getDistributors(context)
        .filter { it != context.packageName }
        .map { DistributorInfo(it, label(it)) }
        .sortedWith(compareByDescending<DistributorInfo> { it.isNtfy }.thenBy { it.label.lowercase() })

    override fun selected(): String? = UnifiedPush.getAckDistributor(context) ?: UnifiedPush.getSavedDistributor(context)

    override fun use(packageName: String): Boolean {
        UnifiedPush.saveDistributor(context, packageName)
        return register()
    }

    override fun reRegister() {
        if (UnifiedPush.getAckDistributor(context) != null) register()
    }

    override fun renew(): Boolean {
        // unregister vergisst auch den Distributor (letzte Registrierung) – deshalb vorher merken.
        val distributor = selected() ?: return false
        UnifiedPush.unregister(context)
        return use(distributor)
    }

    override fun disable() {
        UnifiedPush.unregister(context)
    }

    private fun register(): Boolean = try {
        UnifiedPush.register(context, messageForDistributor = MESSAGE_FOR_DISTRIBUTOR, vapid = vapidPublicKey)
        true
    } catch (error: UnifiedPush.VapidNotValidException) {
        false
    }

    private fun label(packageName: String): String = try {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    } catch (error: PackageManager.NameNotFoundException) {
        packageName
    }

    private companion object {
        const val MESSAGE_FOR_DISTRIBUTOR = "Drink With Me: Getränke von Freunden"
    }
}
