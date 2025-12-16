package com.example.green_pulse_android.helpers

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.mqtt.android.service.MqttAndroidClient
import jakarta.inject.Singleton
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import java.util.UUID

@Module
@InstallIn(SingletonComponent::class)
object MqttModule {

    @Provides
    @Singleton
    fun provideMqttClient(@ApplicationContext context: Context): MqttAndroidClient {
        val clientId = "greenpulse-android-${UUID.randomUUID()}"
        val serverUri = "tcp://10.42.131.124:1883"

        return MqttAndroidClient(context, serverUri, clientId)
    }

    @Provides
    @Singleton
    fun provideMqttConnectOptions(): MqttConnectOptions {
        return MqttConnectOptions().apply {
            isCleanSession = true
            keepAliveInterval = 20
            connectionTimeout = 10
            userName = "app_user"
            password = "appsecure456".toCharArray()
        }
    }
}