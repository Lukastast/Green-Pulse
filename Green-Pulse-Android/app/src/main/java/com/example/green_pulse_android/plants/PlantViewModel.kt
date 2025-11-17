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
    private var isConnecting = false  // Prevent multiple connects

    init {
        Log.d("PlantViewModel", "🔧 ViewModel initialized - starting MQTT setup")
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
        if (isConnecting) return
        isConnecting = true

        val clientId = "greenpulse-android-${UUID.randomUUID()}"  // Unique for Android client
        val serverUri = "tcp://10.42.131.124:1883"  // CHANGE: Use 1883 for MQTT (unsecured). Replace IP with your broker's actual IP
        // For TLS: "ssl://10.42.131.124:8883" and add .setSocketFactory(sslContext.socketFactory) to options

        val client = MqttAndroidClient(context, serverUri, clientId)
        mqttClient = client

        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                Log.d("MQTT", "✅ Connected to MQTT broker (reconnect: $reconnect)")
                isConnecting = false
                // Subscribe after connect
                subscribeToTopics()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.w("MQTT", "⚠️ Connection lost: ${cause?.message}")
                isConnecting = false
                // Simple reconnect retry after 5s
                handler.postDelayed({ setupMqtt() }, 5000)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payloadStr = String(message?.payload ?: byteArrayOf())
                Log.d("MQTT", "📨 Received on $topic: $payloadStr")
                handler.post {
                    try {
                        val data = gson.fromJson(payloadStr, Map::class.java) as Map<String, Any>
                        val plantId = data["plantId"] as String
                        val sensor = data["sensor"] as String
                        val value = (data["value"] as Number).toFloat()
                        val environment = data["environment"] as String
                        val prediction = data["prediction"] as? String  // Optional: Handle "water in 2 hours"

                        // Find and update plant
                        val plantList = _plantsByEnvironment[environment]
                        val plant = plantList?.find { it.id == plantId }
                        plant?.let {
                            when (sensor) {
                                "humidity" -> it.humidity = value
                                "ph" -> it.ph = value
                                "light" -> it.temperature = value  // Reuse temp for light if no field; add if needed
                                // Add "temperature" sensor if publisher sends it
                            }
                            if (!it.alive) it.alive = true  // Assume alive unless dead event

                            // Optional: Handle prediction (e.g., show alert)
                            if (prediction != null) {
                                Log.d("MQTT", "Prediction for $plantId: $prediction")
                                // TODO: Update UI state for alerts
                            }

                            // Sync to Firestore
                            viewModelScope.launch {
                                plantRepository.updatePlantStats(
                                    plant.id, environment, it.humidity, it.ph, it.temperature, it.alive
                                )
                            }
                        } ?: Log.w("MQTT", "Plant $plantId not found in UI")
                    } catch (e: Exception) {
                        Log.e("MQTT", "⚠️ Error parsing message: ${e.message}")
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Not used for subscribe
            }
        })

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 20
            userName = "app_user"  // CHANGE: Use ACL user for subscribe
            password = "your_app_user_password".toCharArray()  // CHANGE: Set actual password
            // For TLS: Add SSLContext setup if using ssl:// URI
        }

        client.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "✅ Connection successful")
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "⚠️ Connection failed: ${exception?.message}")
                isConnecting = false
            }
        })
    }

    private fun subscribeToTopics() {
        mqttClient?.subscribe("greenpulse/#", 1, null, object : IMqttActionListener {  // QoS 1, wildcard for all
            override fun onSuccess(arg0: IMqttToken?) {
                Log.d("MQTT", "✅ Subscribed to greenpulse/#")
            }
            override fun onFailure(arg0: IMqttToken?, arg1: Throwable?) {
                Log.e("MQTT", "⚠️ Subscription failed: ${arg1?.message}")
            }
        })
    }

    // Full addPlant function (unchanged, but now uses correct publish if needed)
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
            qos = 1
        }
        mqttClient?.publish("greenpulse/commands/#", message, null, object : IMqttActionListener {  // CHANGE: Use commands topic per ACL
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("MQTT", "✅ Published: ${payload["action"]}")
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e("MQTT", "⚠️ Publish failed: ${exception?.message}")
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        try {
            mqttClient?.disconnectForcibly(1000, 1000)
        } catch (e: Exception) {
            Log.e("MQTT", "⚠️ Disconnect error: ${e.message}")
        }
        mqttClient = null
    }
}