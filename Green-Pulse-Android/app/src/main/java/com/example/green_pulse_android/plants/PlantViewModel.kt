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
import com.example.green_pulse_android.model.PlantStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class PlantViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plantRepository: PlantRepository  // Inject repo for Firestore
) : ViewModel() {
    private val gson = Gson()
    private val _plantsByEnvironment = mutableStateMapOf<String, SnapshotStateList<Plant>>()  // Group by environment
    val plantsByEnvironment: Map<String, SnapshotStateList<Plant>> = _plantsByEnvironment
    private val _isLoading = mutableStateOf(true)
    val isLoading = _isLoading
    private var mqttClient: MqttAndroidClient? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        setupFirestoreListener()
        setupMqtt()
    }

    private fun setupFirestoreListener() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.w("PlantViewModel", "No user logged in; skipping Firestore listen")
            _isLoading.value = false
            return
        }
        plantRepository.listenToPlants { firestorePlants ->
            Log.d("PlantViewModel", "Firestore plants updated: ${firestorePlants.size}")
            _plantsByEnvironment.clear()
            firestorePlants.forEach { fsPlant ->
                val localPlant = Plant(
                    id = fsPlant.id,
                    type = fsPlant.type,
                    environment = fsPlant.environment,
                    name = fsPlant.name,
                    alive = fsPlant.alive,
                    humidity = fsPlant.humidity,
                    ph = fsPlant.ph,
                    temperature = fsPlant.temperature
                )
                _plantsByEnvironment.getOrPut(fsPlant.environment) { mutableStateListOf() }.add(localPlant)
            }
            _isLoading.value = false
        }
    }

    public fun refreshPlants() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // Trigger a manual query or just log to verify listener
            Log.d("PlantViewModel", "Manual refresh triggered for user $userId")
        }
    }
    private fun setupMqtt() {
        val clientId = "greenpulse-viewer-${UUID.randomUUID()}"
        val serverUri = "tcp://10.0.2.2:1883"  // Adjust for device/host IP
        val client = MqttAndroidClient(context, serverUri, clientId)
        mqttClient = client

        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("✅ Connected to MQTT broker")
            }

            override fun connectionLost(cause: Throwable?) {
                println("⚠️ Connection lost: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payloadStr = String(message?.payload ?: byteArrayOf())
                handler.post {
                    try {
                        when {
                            payloadStr.contains("\"event\":\"plant_dead\"") -> {
                                @Suppress("UNCHECKED_CAST")
                                val payloadMap = gson.fromJson(payloadStr, MutableMap::class.java) as MutableMap<String, Any>
                                val plantId = payloadMap["plantId"] as String
                                _plantsByEnvironment.values.forEach { list ->
                                    val plant = list.find { it.id == plantId }
                                    plant?.alive = false
                                }
                            }
                            payloadStr.contains("\"event\":\"status\"") -> {
                                val status = gson.fromJson(payloadStr, PlantStatus::class.java)
                                _plantsByEnvironment.values.forEach { list ->
                                    val plant = list.find { it.id == status.plantId }
                                    plant?.let {
                                        it.alive = status.alive
                                        it.humidity = status.humidity
                                        it.ph = status.ph
                                        it.temperature = status.temperature
                                        // Sync back to Firestore in coroutine
                                        viewModelScope.launch {
                                            plantRepository.updatePlantStats(
                                                plant.id,
                                                plant.environment,
                                                status.humidity,
                                                status.ph,
                                                status.temperature,
                                                status.alive
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("⚠️ Error parsing message: ${e.message}")
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            userName = "admin"
            password = "public".toCharArray()
        }

        client.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                println("✅ Connection successful")
                client.subscribe("greenpulse/simulator", 0, null, object : IMqttActionListener {
                    override fun onSuccess(arg0: IMqttToken?) {
                        println("✅ Subscribed to topic")
                    }
                    override fun onFailure(arg0: IMqttToken?, arg1: Throwable?) {
                        println("⚠️ Subscription failed: ${arg1?.message}")
                    }
                })
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                println("⚠️ Connection failed: ${exception?.message}")
            }
        })
    }

    // Full addPlant function
    fun addPlant(type: String, environment: String, name: String) {
        viewModelScope.launch {
            val plantId = UUID.randomUUID().toString()
            val payload = mapOf(
                "action" to "add_plant",
                "plantId" to plantId,
                "type" to type
            )
            publishMessage(payload)

            // Create in Firestore first
            val firestorePlant = FirestorePlant(
                id = plantId,
                name = name,
                type = type,
                environment = environment
            )
            val result = plantRepository.createPlant(firestorePlant, environment)
            if (result.isSuccess) {
                // Local add for immediate UI
                val localPlant = Plant(
                    id = plantId,
                    type = type,
                    environment = environment,
                    name = name,
                    humidity = Random.nextFloat() * 40f + 40f,
                    ph = Random.nextFloat() * 1.5f + 6.0f,
                    temperature = Random.nextFloat() * 10f + 18f
                )
                _plantsByEnvironment.getOrPut(environment) { mutableStateListOf() }.add(localPlant)
                Log.d("PlantViewModel", "Plant added successfully to Firestore and UI")
            } else {
                Log.e("PlantViewModel", "Failed to add plant to Firestore", result.exceptionOrNull())
            }
        }
    }

    private fun publishMessage(payload: Map<String, Any>) {
        val message = MqttMessage().apply {
            this.payload = gson.toJson(payload).toByteArray()
        }
        mqttClient?.publish("greenpulse/simulator", message, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                println("✅ Published: ${payload["action"]}")
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                println("⚠️ Publish failed: ${exception?.message}")
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        try {
            mqttClient?.disconnectForcibly(1000, 1000)
        } catch (e: Exception) {
            println("⚠️ Disconnect error: ${e.message}")
        }
        mqttClient = null
    }
}