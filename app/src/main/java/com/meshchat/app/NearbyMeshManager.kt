package com.meshchat.app

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.*

class NearbyMeshManager(private val context: Context) {

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectedEndpoints = mutableSetOf<String>()
    val activePeersFlow = MutableStateFlow<List<String>>(emptyList())
    
    private val seenMessageIds = object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>?): Boolean {
            return size > 1000
        }
    }

    val localNodeId: String by lazy {
        context.getSharedPreferences("mesh_prefs", Context.MODE_PRIVATE)
            .getString("node_id", null) ?: UUID.randomUUID().toString().also {
                context.getSharedPreferences("mesh_prefs", Context.MODE_PRIVATE)
                    .edit().putString("node_id", it).apply()
            }
    }

    var onMessageReceived: ((MeshEnvelope, String) -> Unit)? = null

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(
                localNodeId, endpointId, connectionLifecycleCallback
            )
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoints.add(endpointId)
                    activePeersFlow.value = connectedEndpoints.toList()
                }
                else -> connectedEndpoints.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            activePeersFlow.value = connectedEndpoints.toList()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    scope.launch { processIncomingPacket(bytes, endpointId) }
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun startMeshNetwork() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .setLowPower(false)
            .build()

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .setLowPower(false)
            .build()

        connectionsClient.startAdvertising(
            localNodeId, context.packageName, connectionLifecycleCallback, advertisingOptions
        )
        connectionsClient.startDiscovery(
            context.packageName, endpointDiscoveryCallback, discoveryOptions
        )
    }

    fun stopMeshNetwork() {
        connectionsClient.stopAllEndpoints()
    }

    fun sendBroadcast(payloadType: PayloadType, data: ByteArray, mediaId: String = "", chunkIndex: Int = -1, totalChunks: Int = -1) {
        val envelope = MeshEnvelope(
            messageId = UUID.randomUUID().toString(),
            senderId = localNodeId,
            recipientId = "BROADCAST",
            payloadType = payloadType,
            data = data,
            mediaId = mediaId,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks
        )
        routePacket(envelope, null)
    }

    private fun processIncomingPacket(bytes: ByteArray, sourceEndpointId: String) {
        try {
            val envelope = json.decodeFromString(MeshEnvelope.serializer(), String(bytes))
            
            synchronized(seenMessageIds) {
                if (seenMessageIds.containsKey(envelope.messageId)) return
                seenMessageIds[envelope.messageId] = true
            }

            if (envelope.recipientId == localNodeId || envelope.recipientId == "BROADCAST") {
                onMessageReceived?.invoke(envelope, sourceEndpointId)
            }

            if (envelope.ttl > 1) {
                val relayEnvelope = envelope.copy(ttl = envelope.ttl - 1)
                routePacket(relayEnvelope, sourceEndpointId)
            }
        } catch (e: Exception) {
            Log.e("NearbyMeshManager", "Packet parsing failed", e)
        }
    }

    private fun routePacket(envelope: MeshEnvelope, excludeEndpointId: String?) {
        val payloadBytes = json.encodeToString(MeshEnvelope.serializer(), envelope).toByteArray()
        val payload = Payload.fromBytes(payloadBytes)

        for (endpoint in connectedEndpoints) {
            if (endpoint != excludeEndpointId) {
                connectionsClient.sendPayload(endpoint, payload)
            }
        }
    }
}
