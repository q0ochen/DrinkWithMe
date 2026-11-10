package com.drinkwithme.app.config

import com.drinkwithme.app.BuildConfig

/** Server-Werte dieses Builds (aus `supabase.properties`); `null` = „Server noch nicht eingerichtet“. */
fun serverConfigFromBuild(): ServerConfig? = ServerConfig.parse(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    anonKey = BuildConfig.SUPABASE_ANON_KEY,
    vapidPublicKey = BuildConfig.VAPID_PUBLIC_KEY,
    webAppUrl = BuildConfig.WEB_APP_URL,
)
