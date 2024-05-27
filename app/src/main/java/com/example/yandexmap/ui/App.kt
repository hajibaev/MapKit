package com.example.yandexmap.ui

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        MapKitFactory.setApiKey(MAP_API_KEY)
    }

    companion object {
        private const val MAP_API_KEY = "b101f532-6a1b-4f78-b926-7adb75a3491b"
    }
}
