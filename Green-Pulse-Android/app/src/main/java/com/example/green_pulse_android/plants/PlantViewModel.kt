package com.example.green_pulse_android.plants

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.green_pulse_android.firebase.PlantRepository
import com.example.green_pulse_android.model.FirestorePlant
import com.example.green_pulse_android.model.Plant
import com.example.green_pulse_android.model.PlantHistory
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlantViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plantRepository: PlantRepository,
    private val mqttClient: MqttAndroidClient,
    private val connectOptions: MqttConnectOptions
) : ViewModel() {

    private val gson = Gson()
    private val _plantsByEnvironment = mutableStateMapOf<String, SnapshotStateList<Plant>>()
    val plantsByEnvironment: Map<String, SnapshotStateList<Plant>> = _plantsByEnvironment
    private val _isLoading = mutableStateOf(true)
    val isLoading = _isLoading
    private val handler = Handler(Looper.getMainLooper())
    private var isConnecting = false

    init {
        Log.d("PlantViewModel", "ViewModel initialized")
        setupFirestoreListener()
        connectMqttIfNeeded()
    }

    private fun setupFirestoreListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w("PlantViewModel", "No user logged in")
            _isLoading.value = false
            return
        }

        plantRepository.listenToPlants { firestorePlants ->
            Log.d("PlantViewModel", "Firestore updated: ${firestorePlants.size} plants")
            _plantsByEnvironment.clear()
            firestorePlants.forEach { fsPlant ->
                val plant = Plant(
                    id = fsPlant.id,
                    type = fsPlant.type,
                    environment = fsPlant.environment,
                    name = fsPlant.name,
                    alive = fsPlant.alive,
                    humidity = fsPlant.humidity,
                    ph = fsPlant.ph,
                    temperature = fsPlant.temperature
                )
                _plantsByEnvironment.getOrPut(fsPlant.environment) { mutableStateListOf() }.add(plant)
            }
            _isLoading.value = false
        }
    }

    private fun connectMqttIfNeeded() {
        if (isConnecting || mqttClient.isConnected) return
        isConnecting = true

        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d("MQTT", "Connected! Reconnect: $reconnect")
                isConnecting = false
                subscribeToTopics()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w("MQTT", "Connection lost", cause)
                isConnecting = false
                handler.postDelayed({ connectMqttIfNeeded() }, 5000)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payloadStr = String(message?.payload ?: byteArrayOf())
                Log.d("MQTT", "Received on $topic: $payloadStr")

                handler.post {
                    try {
                        val data = gson.fromJson(payloadStr, Map::class.java) as Map<String, Any>
                        val event = data["event"] as? String

                        when (event) {
                            "status" -> {
                                val plantId = data["plantId"] as String
                                val humidity = (data["humidity"] as? Number)?.toFloat() ?: return@post
                                val ph = (data["ph"] as? Number)?.toFloat() ?: return@post
                                val temperature = (data["temperature"] as? Number)?.toFloat() ?: return@post
                                val alive = data["alive"] as? Boolean ?: true

                                updatePlantAndHistory(plantId, humidity, ph, temperature, alive)
                            }
                            "plant_dead" -> {
                                val plantId = data["plantId"] as String
                                markPlantDead(plantId)
                            }
                            "plant_added" -> {
                                Log.d("MQTT", "New plant added remotely: ${data["plantId"]}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MQTT", "Parse error", e)
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        mqttClient.connect(connectOptions, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "Connection successful")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "Connection failed", exception)
                isConnecting = false
            }
        })
    }

    private fun subscribeToTopics() {
        mqttClient.subscribe("greenpulse/simulator", 1, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "Subscribed to greenpulse/simulator")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "Subscribe failed", exception)
            }
        })
    }

    fun refreshPlants() {
        Log.d("PlantViewModel", "Refresh triggered")
        _isLoading.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            _isLoading.value = false
        }
    }
    private fun updatePlantAndHistory(
        plantId: String,
        humidity: Float,
        ph: Float,
        temperature: Float,
        alive: Boolean
    ) {
        _plantsByEnvironment.values.forEach { list ->
            val plant = list.find { it.id == plantId } ?: return@forEach
            plant.apply {
                this.humidity = humidity
                this.ph = ph
                this.temperature = temperature
                this.alive = alive
            }

            viewModelScope.launch {
                plantRepository.updatePlantStats(plant.id, plant.environment, humidity, ph, temperature, alive)
                plantRepository.addHistoryEntry(
                    plantId = plant.id,
                    environment = plant.environment,
                    history = PlantHistory(
                        timestamp = Timestamp.now(),
                        humidity = humidity,
                        ph = ph,
                        temperature = temperature,
                        alive = alive
                    )
                )
            }
        }
    }

    private fun markPlantDead(plantId: String) {
        _plantsByEnvironment.values.forEach { list ->
            list.find { it.id == plantId }?.alive = false
        }
    }

    fun deletePlant(plant: Plant) {
        viewModelScope.launch {
            val result = plantRepository.deletePlant(plant.id, plant.environment)
            result.onFailure { e ->
                Log.e("PlantViewModel", "Delete failed", e)
            }
        }
    }

    fun addPlant(type: String, environment: String, name: String) {
        viewModelScope.launch {
            val plantId = UUID.randomUUID().toString()
            val payload = mapOf(
                "action" to "add_plant",
                "plantId" to plantId,
                "type" to type,
                "environment" to environment
            )
            publishMessage(payload)

            val firestorePlant = FirestorePlant(
                id = plantId,
                name = name,
                type = type,
                environment = environment
            )
            plantRepository.createPlant(firestorePlant, environment)
        }
    }

    private fun publishMessage(payload: Map<String, Any>) {
        val message = MqttMessage(gson.toJson(payload).toByteArray()).apply { qos = 1 }
        mqttClient.publish("greenpulse/newplant", message, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) = Unit
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "Publish failed", exception)
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (mqttClient.isConnected) {
                mqttClient.disconnectForcibly(1000, 1000)
            }
        } catch (e: Exception) {
            Log.e("MQTT", "Disconnect error", e)
        }
    }
}