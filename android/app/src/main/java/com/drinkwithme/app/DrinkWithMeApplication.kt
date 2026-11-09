package com.drinkwithme.app

import android.app.Application
import com.drinkwithme.app.di.AppContainer
import com.drinkwithme.app.di.DefaultAppContainer

class DrinkWithMeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
