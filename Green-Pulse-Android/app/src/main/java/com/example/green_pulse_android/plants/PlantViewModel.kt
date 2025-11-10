package com.example.green_pulse_android.plants

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.green_pulse_android.GreenPulseViewModel
import com.google.gson.Gson
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.security.SecureRandom
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class Plant(val id: String, val type: String, var alive: Boolean = true)

class PlantViewModel(private val context: Context) : GreenPulseViewModel() {
    private val gson = Gson()
    private val _plantsByType = mutableStateMapOf<String, SnapshotStateList<Plant>>()
    val plantsByType: Map<String, SnapshotStateList<Plant>> = _plantsByType

    private var mqttClient: MqttAndroidClient? = null
    private val handler = Handler(Looper.getMainLooper())

    init {
        setupMqtt()
    }

    private fun setupMqtt() {
        val clientId = "greenpulse-viewer-${UUID.randomUUID()}"
        val serverUri = "ssl://broker.emqx.io:8883"
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
                        @Suppress("UNCHECKED_CAST")
                        val payloadMap = gson.fromJson(payloadStr, MutableMap::class.java) as MutableMap<String, Any>
                        if (payloadMap["event"] == "plant_dead") {
                            val plantId = payloadMap["plantId"] as String
                            _plantsByType.forEach { (_, list) ->
                                val plant = list.find { it.id == plantId }
                                plant?.alive = false
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
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLSv1.2").apply {
                init(null, trustAllCerts, SecureRandom())
            }
            socketFactory = sslContext.socketFactory
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

    fun addPlant(type: String) {
        val plantId = UUID.randomUUID().toString()
        val payload = mapOf(
            "action" to "add_plant",
            "plantId" to plantId,
            "type" to type
        )
        publishMessage(payload)
        _plantsByType.getOrPut(type) { mutableStateListOf() }.add(Plant(plantId, type))
    }

    fun waterPlant(plantId: String) {
        val payload = mapOf(
            "action" to "water",
            "plantId" to plantId,
            "amount" to 0.3
        )
        publishMessage(payload)
    }

    fun setPhPlant(plantId: String) {
        val payload = mapOf(
            "action" to "set_ph",
            "plantId" to plantId,
            "target" to 6.5
        )
        publishMessage(payload)
    }

    fun setTempPlant(plantId: String) {
        val payload = mapOf(
            "action" to "set_temp",
            "plantId" to plantId,
            "temp" to 22.0
        )
        publishMessage(payload)
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

class PlantViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlantViewModel::class.java)) {
            return PlantViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
