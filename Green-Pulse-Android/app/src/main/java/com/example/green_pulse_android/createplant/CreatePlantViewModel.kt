package com.example.green_pulse_android.createplant

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.green_pulse_android.firebase.PlantRepository
import com.example.green_pulse_android.model.FirestorePlant
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import javax.inject.Inject


@HiltViewModel
class CreatePlantViewModel @Inject constructor(
    private val plantRepository: PlantRepository,
    private val mqttClient: MqttAndroidClient,
    private val connectOptions: MqttConnectOptions
) : ViewModel() {

    fun createPlant(
        plant: FirestorePlant,
        environment: String,
        onResult: (FirestorePlant?) -> Unit,
        onSuccessRefresh: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val result = plantRepository.createPlant(plant, environment)
                result.fold(
                    onSuccess = { docId ->
                        val savedPlant = plant.copy(id = docId)
                        onResult(savedPlant)
                        onSuccessRefresh?.invoke()
                        publishNewPlantToMqtt(savedPlant, environment)

                        Log.d("CreatePlantVM", "Plant created + MQTT notified: ${savedPlant.id}")
                    },
                    onFailure = { e ->
                        Log.e("CreatePlantVM", "Create failed", e)
                        onResult(null)
                    }
                )
            } catch (e: Exception) {
                Log.e("CreatePlantVM", "Unexpected error", e)
                onResult(null)
            }
        }
    }
    private fun publishNewPlantToMqtt(plant: FirestorePlant, environment: String) {
        viewModelScope.launch {
            val payload = mapOf(
                "action" to "add_plant",
                "plantId" to plant.id,
                "type" to plant.type,
                "environment" to environment
            )

            val message = MqttMessage().apply {
                this.payload = Gson().toJson(payload).toByteArray()
                qos = 1
            }

            mqttClient.publish("greenpulse/newplant", message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "Sent new plant to simulator: ${plant.id}")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Failed to notify simulator", exception)
                }
            })
        }
    }
}