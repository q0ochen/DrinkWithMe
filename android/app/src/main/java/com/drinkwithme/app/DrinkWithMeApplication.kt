package com.drinkwithme.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.drinkwithme.app.di.AppContainer

/** Erzeugt beim Start die Kompositionswurzel (auch für WorkManager-Aufträge und Push im Hintergrund). */
class DrinkWithMeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onProcessStarted()
        // Nur ein echter Wechsel der App in den Vordergrund – nicht jede Rückkehr der Activity aus einem Dialog,
        // der Spracherkennung oder dem Teilen-Menü (das wäre jedes Mal eine Runde Anfragen).
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.onAppForeground()
            },
        )
    }
}
