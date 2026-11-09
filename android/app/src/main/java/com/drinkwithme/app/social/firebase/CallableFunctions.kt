package com.drinkwithme.app.social.firebase

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Ruft aufrufbare Cloud Functions (Region europe-west3) auf und übersetzt Fehler in deutsche Meldungen. */
class CallableFunctions(
    private val functions: FirebaseFunctions,
    private val errors: FunctionsErrorTranslator = FunctionsErrorTranslator(),
) {

    /**
     * @param timeoutSeconds eigener Timeout für lange laufende Funktionen (Standard des SDK: 70 s).
     * @return die Antwort der Funktion (Maps/Listen/Werte), z. B. `{ group: GroupDto }`.
     */
    suspend fun call(functionName: String, input: Map<String, Any>, timeoutSeconds: Long? = null): Any? = try {
        val callable = functions.getHttpsCallable(functionName)
        timeoutSeconds?.let { callable.setTimeout(it, TimeUnit.SECONDS) }
        // Bewusst getData() statt .data: In firebase-functions 21.1.0 (BoM 33.6/33.7) ist das Kotlin-Property
        // `data` privat (firebase-android-sdk#6522); getData() ist in allen Versionen öffentlich.
        callable.call(input).await().getData()
    } catch (error: FirebaseFunctionsException) {
        throw errors.translate(error, functionName)
    }
}
