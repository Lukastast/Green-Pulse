package com.example.green_pulse_android.model

import com.google.firebase.Timestamp
import java.util.UUID

data class FirestorePlant(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: String = "Basil",
    val environment: String = "",
    var alive: Boolean = true,
    var humidity: Float = 50f,
    var ph: Float = 6.5f,
    var temperature: Float = 22f,
    val createdAt: Timestamp = Timestamp.now()
)

data class PlantHistory(
    val timestamp: Timestamp = Timestamp.now(),
    val alive: Boolean,
    val humidity: Float,
    val ph: Float,
    val temperature: Float
) {
    constructor() : this(Timestamp.now(), false, 0f, 0f, 0f)
}