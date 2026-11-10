package com.drinkwithme.app

import android.app.Application
import com.drinkwithme.app.di.AppContainer

/** Erzeugt beim Start die Kompositionswurzel (auch für WorkManager-Aufträge und Push im Hintergrund). */
class DrinkWithMeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onProcessStarted()
    }
}
