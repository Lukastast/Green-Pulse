package com.example.green_pulse_android.model

import com.google.firebase.Timestamp
import java.util.UUID

data class FirestorePlant(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String = "",
    val type: String = "Basil",
    val environment: String = "Indoors",  // "Indoors", "Outdoors", "Greenhouse"
    var alive: Boolean = true,
    var humidity: Float = 50f,
    var ph: Float = 6.5f,
    var temperature: Float = 22f,
    val createdAt: Timestamp = Timestamp.now()
)