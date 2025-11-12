package com.example.green_pulse_android.model

import com.google.gson.annotations.SerializedName

data class Plant(
    val id: String,
    val type: String,
    val environment: String,
    val name: String,
    var alive: Boolean = true,
    var humidity: Float = 50f,
    var ph: Float = 6.5f,
    var temperature: Float = 22f
)

data class PlantStatus(
    @SerializedName("event") val event: String,
    @SerializedName("plantId") val plantId: String,
    @SerializedName("humidity") val humidity: Float,
    @SerializedName("ph") val ph: Float,
    @SerializedName("temperature") val temperature: Float,
    @SerializedName("alive") val alive: Boolean
)
