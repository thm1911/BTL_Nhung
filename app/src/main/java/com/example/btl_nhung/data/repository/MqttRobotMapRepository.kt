package com.example.btl_nhung.data.repository

import android.util.Log
import com.example.btl_nhung.BuildConfig
import com.example.btl_nhung.data.remote.dto.PoseStateDto
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class MqttRobotMapRepository @Inject constructor(
    private val gson: Gson,
) : RobotMapRepository {

    private companion object {
        const val TAG = "MqttRobotMapRepo"
        const val TOPIC_POSE = "robot2wd/status/pose"
        const val TOPIC_CMD = "robot2wd/cmd"
        const val QOS = 1
        const val RECONNECT_DELAY_MS = 3_000L
    }

    private val _pose = MutableStateFlow(
        PoseStateDto(
            x = 0.0,
            y = 0.0,
            t = 0.0,
            ts = System.currentTimeMillis(),
        ),
    )
    override val pose: StateFlow<PoseStateDto> = _pose.asStateFlow()

    private val _lastControlJson = MutableStateFlow<String?>(null)
    override val lastControlJson: StateFlow<String?> = _lastControlJson.asStateFlow()

    private val _lastTargetJson = MutableStateFlow<String?>(null)
    override val lastTargetJson: StateFlow<String?> = _lastTargetJson.asStateFlow()
    private val mqttScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mqttBrokerUri =
        "${BuildConfig.MQTT_SCHEME}://${BuildConfig.MQTT_HOST}:${BuildConfig.MQTT_PORT}"
    private val mqttClient by lazy {
        MqttAsyncClient(
            mqttBrokerUri,
            MqttClient.generateClientId(),
            MemoryPersistence(),
        )
    }

    init {
        if (BuildConfig.MQTT_HOST.isBlank() || BuildConfig.MQTT_USER.isBlank() || BuildConfig.MQTT_PASS.isBlank()) {
            Log.w(TAG, "MQTT config missing. Skip setup.")
            // Skip MQTT setup when config is missing.
        } else {
            if (BuildConfig.MQTT_SCHEME.equals("tcp", ignoreCase = true) && BuildConfig.MQTT_PORT == 8883) {
                Log.w(TAG, "Config mismatch: tcp:// with port 8883 often fails on HiveMQ Cloud. Use ssl:// for 8883.")
            }
            Log.i(TAG, "Init MQTT with broker=$mqttBrokerUri user=${BuildConfig.MQTT_USER}")
            mqttClient.setCallback(
                object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        Log.i(TAG, "MQTT connectComplete reconnect=$reconnect server=$serverURI")
                        subscribeToTopics()
                    }

                    override fun connectionLost(cause: Throwable?) {
                        Log.e(TAG, "MQTT connection lost: ${cause?.message}", cause)
                        requestReconnect("connectionLost")
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic == null || message == null) return
                        val payload = message.toString()
                        Log.d(TAG, "MQTT recv topic=$topic payload=$payload")
                        when (topic) {
                            TOPIC_POSE -> handlePoseMessage(payload)
                            TOPIC_CMD -> handleCommandMessage(payload)
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.d(TAG, "MQTT deliveryComplete messageId=${token?.messageId}")
                    }
                },
            )
            connect()
        }
    }

    override fun applyPoseFromDevice(dto: PoseStateDto) {
        _pose.value = dto
    }

    override suspend fun sendSetOrigin(): Result<Unit> {
        val stopJson = JsonObject().apply { addProperty("type", "stop") }.toString()
        return publish(TOPIC_CMD, stopJson).onSuccess {
            _lastControlJson.value = stopJson
        }
    }

    override suspend fun sendReset(): Result<Unit> {
        val resetJson = JsonObject().apply { addProperty("type", "reset") }.toString()
        return publish(TOPIC_CMD, resetJson).onSuccess {
            _lastControlJson.value = resetJson
        }
    }

    override suspend fun sendTarget(xCm: Double, yCm: Double): Result<Unit> {
        val moveJson = JsonObject().apply {
            addProperty("type", "move")
            addProperty("x", xCm)
            addProperty("y", yCm)
        }.toString()
        return publish(TOPIC_CMD, moveJson).onSuccess {
            _lastTargetJson.value = moveJson
        }
    }

    private fun connect() {
        if (BuildConfig.MQTT_HOST.isBlank() || BuildConfig.MQTT_USER.isBlank() || BuildConfig.MQTT_PASS.isBlank()) {
            Log.w(TAG, "MQTT connect skipped due to empty config.")
            return
        }
        if (mqttClient.isConnected) {
            Log.d(TAG, "MQTT already connected, ensure subscriptions.")
            subscribeToTopics()
            return
        }
        val options = MqttConnectOptions().apply {
            userName = BuildConfig.MQTT_USER
            password = BuildConfig.MQTT_PASS.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
            keepAliveInterval = 20
            connectionTimeout = 10
        }
        Log.i(TAG, "MQTT connecting to $mqttBrokerUri")
        mqttClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(TAG, "MQTT connect success.")
                subscribeToTopics()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e(TAG, "MQTT connect failed: ${exception?.message}", exception)
                requestReconnect("connectFailure")
            }
        })
    }

    private fun requestReconnect(reason: String) {
        if (BuildConfig.MQTT_HOST.isBlank() || BuildConfig.MQTT_USER.isBlank() || BuildConfig.MQTT_PASS.isBlank()) return
        mqttScope.launch {
            while (isActive && !mqttClient.isConnected) {
                Log.w(TAG, "Reconnect retry in ${RECONNECT_DELAY_MS}ms (reason=$reason)")
                delay(RECONNECT_DELAY_MS)
                runCatching { connect() }
            }
        }
    }

    private fun subscribeToTopics() {
        if (!mqttClient.isConnected) {
            Log.w(TAG, "Subscribe skipped: MQTT not connected.")
            return
        }
        mqttClient.subscribe(TOPIC_POSE, QOS, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(TAG, "Subscribed topic=$TOPIC_POSE qos=$QOS")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e(TAG, "Subscribe failed topic=$TOPIC_POSE: ${exception?.message}", exception)
            }
        })
        mqttClient.subscribe(TOPIC_CMD, QOS, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(TAG, "Subscribed topic=$TOPIC_CMD qos=$QOS")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.e(TAG, "Subscribe failed topic=$TOPIC_CMD: ${exception?.message}", exception)
            }
        })
    }

    private suspend fun publish(topic: String, payload: String): Result<Unit> {
        if (!mqttClient.isConnected) {
            connect()
        }
        if (!mqttClient.isConnected) {
            Log.w(TAG, "Publish skipped topic=$topic because MQTT not connected.")
            return Result.failure(IllegalStateException("MQTT chưa kết nối tới broker."))
        }
        Log.i(TAG, "Publish topic=$topic payload=$payload")
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                mqttClient.publish(topic, payload.toByteArray(), QOS, false, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "Publish success topic=$topic")
                        continuation.resume(Result.success(Unit))
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e(TAG, "Publish failed topic=$topic: ${exception?.message}", exception)
                        continuation.resume(
                            Result.failure(
                                exception ?: IllegalStateException("Publish MQTT thất bại."),
                            ),
                        )
                    }
                })
            }
        }
    }

    private fun handlePoseMessage(payload: String) {
        runCatching {
            val dto = gson.fromJson(payload, PoseStateDto::class.java)
            val ts = if (dto.ts == 0L) System.currentTimeMillis() else dto.ts
            _pose.value = dto.copy(ts = ts)
            Log.d(TAG, "Pose updated x=${dto.x} y=${dto.y} t=${dto.t} ts=$ts")
        }.onFailure { e ->
            Log.e(TAG, "Parse pose failed payload=$payload error=${e.message}", e)
        }
    }

    private fun handleCommandMessage(payload: String) {
        runCatching {
            val json = JsonParser.parseString(payload).asJsonObject
            when (json.get("type")?.asString?.lowercase()) {
                "stop" -> _lastControlJson.value = payload
                "reset" -> _lastControlJson.value = payload
                "move" -> _lastTargetJson.value = payload
            }
        }.onFailure { e ->
            Log.e(TAG, "Parse cmd failed payload=$payload error=${e.message}", e)
        }
    }
}
