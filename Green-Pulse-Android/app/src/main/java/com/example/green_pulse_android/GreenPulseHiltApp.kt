package com.example.green_pulse_android

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.firestore
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp class GreenPulseHiltApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)  // Ensures Firebase is ready before Hilt

        val firestore = Firebase.firestore
        firestore.clearPersistence().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("App", "Firestore cache cleared successfully")
            } else {
                Log.w("App", "Failed to clear Firestore cache", task.exception)
            }
        }
    }
}