package com.example.crimicam.facerecognitionnetface.models.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object FirestoreStore {
    lateinit var firestore: FirebaseFirestore
        private set

    fun init(context: Context) {
        // Initialize Firebase if not already initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        firestore = FirebaseFirestore.getInstance()

        // Optional: Configure Firestore settings
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true) // Enable offline persistence
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        firestore.firestoreSettings = settings
    }
}