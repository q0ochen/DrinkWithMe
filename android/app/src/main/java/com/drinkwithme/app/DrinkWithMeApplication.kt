package com.drinkwithme.app

import android.app.Application
import com.drinkwithme.app.di.AppContainer
import com.drinkwithme.app.di.DefaultAppContainer
import com.drinkwithme.app.push.DrinkNotificationChannel

class DrinkWithMeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        if (container.isSocialAvailable) {
            DrinkNotificationChannel.create(this)
            container.pushHandler?.onAppStarted()
        }
    }
}
