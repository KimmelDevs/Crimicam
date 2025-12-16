package com.example.crimicam

import android.app.Application
import com.example.crimicam.di.appModule
import com.google.firebase.FirebaseApp
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class CrimiCamApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        FirebaseApp.initializeApp(this)

        // Initialize Koin
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@CrimiCamApplication)
            modules(appModule)
        }
    }
}