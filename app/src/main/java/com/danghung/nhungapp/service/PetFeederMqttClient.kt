package com.danghung.nhungapp.service

import android.content.Context
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class PetFeederMqttClient(_context: Context) {

    interface Listener {
        fun onConnected()
        fun onFoodDistance(distanceCm: Int)
        fun onFeedDoneReport()
        fun onAutoSensorStateChanged(enabled: Boolean) {}
        fun onError(message: String)
    }

    companion object {
        private const val BROKER_HOST = "p6666028.ala.asia-southeast1.emqxsl.com"
        private const val BROKER_PORT = 8883
        private const val USERNAME = "freertos"
        private const val PASSWORD = "freertos"

        private const val TOPIC_FED = "fed"
        private const val TOPIC_FOOD_STATUS = "food_status"
        private const val TOPIC_STATUS = "status"
        private const val TOPIC_AUTO_SENSOR = "auto_sensor"
        private const val TOPIC_FOOD_CHECK = "food_check"
    }

    private val serverUri = "ssl://$BROKER_HOST:$BROKER_PORT"
    private val clientId = "nhungapp_${UUID.randomUUID()}"
    private val mqttClient = MqttAsyncClient(serverUri, clientId, MemoryPersistence())

    private var listener: Listener? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    private fun buildInsecureSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        return sc.socketFactory
    }

    fun connect() {
        if (mqttClient.isConnected) {
            listener?.onConnected()
            return
        }

        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                subscribeDefaultTopics()
                listener?.onConnected()
            }

            override fun connectionLost(cause: Throwable?) {
                listener?.onError(cause?.message ?: "Mất kết nối MQTT")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.toString()?.trim().orEmpty()

                when (topic) {

                    TOPIC_FOOD_STATUS -> {
                        payload.toIntOrNull()?.let {
                            listener?.onFoodDistance(it)
                        }
                    }

                    TOPIC_STATUS -> {
                        when (payload) {
                            "OPEN_FED" -> listener?.onFeedDoneReport()
                            "AUTO_ON" -> listener?.onAutoSensorStateChanged(true)
                            "AUTO_OFF" -> listener?.onAutoSensorStateChanged(false)
                        }
                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false
            userName = USERNAME
            password = PASSWORD.toCharArray()
            socketFactory = buildInsecureSslSocketFactory()
        }

        mqttClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(token: org.eclipse.paho.client.mqttv3.IMqttToken?) {
                subscribeDefaultTopics()
                listener?.onConnected()
            }

            override fun onFailure(
                token: org.eclipse.paho.client.mqttv3.IMqttToken?,
                exception: Throwable?
            ) {
                listener?.onError(exception?.message ?: "Kết nối MQTT thất bại")
            }
        })
    }

    fun disconnect() {
        if (!mqttClient.isConnected) return
        mqttClient.disconnect()
    }

    fun publishFeedNow() {
        publish(TOPIC_FED, "FED")
    }

    fun publishAutoSensor(enabled: Boolean) {
        publish(TOPIC_AUTO_SENSOR, if (enabled) "ON" else "OFF")
    }

    fun requestFoodStatus() {
        publish(TOPIC_FOOD_CHECK, "CHECK")
    }

    private fun publish(topic: String, payload: String) {
        if (!mqttClient.isConnected) {
            listener?.onError("MQTT chưa kết nối")
            return
        }
        val message = MqttMessage(payload.toByteArray()).apply { qos = 1 }
        mqttClient.publish(topic, message)
    }

    private fun subscribeDefaultTopics() {
        if (!mqttClient.isConnected) return
        mqttClient.subscribe(TOPIC_FOOD_STATUS, 1)
        mqttClient.subscribe(TOPIC_STATUS, 1)
    }
}
