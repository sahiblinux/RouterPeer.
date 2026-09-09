package com.example.mesh

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.crypto.CryptoManager
import com.example.crypto.IdentityPayload
import com.example.crypto.LocalKeyring
import com.example.crypto.decodeHex
import com.example.crypto.toHex
import com.example.data.db.AppDatabase
import com.example.data.db.GroupEntity
import com.example.data.db.MessageEntity
import com.example.data.db.PeerEntity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MeshStats(
    val activeLinks: Int = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val packetsRouted: Long = 0
)

class NearbyMeshManager(
    private val context: Context,
    private val database: AppDatabase,
    private val keyring: LocalKeyring
) {
    private val TAG = "NearbyMeshManager"
    private val SERVICE_ID = "com.example.routerpeer.p2p.mesh"
    private val STRATEGY = Strategy.P2P_CLUSTER

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active connected endpoints: endpointId -> PeerNodeId
    private val _connectedEndpoints = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectedEndpoints: StateFlow<Map<String, String>> = _connectedEndpoints.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _stats = MutableStateFlow(MeshStats())
    val stats: StateFlow<MeshStats> = _stats.asStateFlow()

    // Deduplication LRU cache to prevent mesh routing loops and broadcast storms
    private val seenPacketIds = Collections.newSetFromMap(
        object : LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 2000
            }
        }
    )

    private val endpointToPeerNodeId = ConcurrentHashMap<String, String>()

    init {
        scope.launch {
            database.peerDao().markAllDisconnected()
        }
    }

    fun startMesh() {
        startAdvertising()
        startDiscovery()
    }

    fun stopMesh() {
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
        endpointToPeerNodeId.clear()
        _connectedEndpoints.value = emptyMap()
        _stats.update { it.copy(activeLinks = 0) }
        scope.launch {
            database.peerDao().markAllDisconnected()
        }
    }

    fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            keyring.nodeId,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            _isAdvertising.value = true
            Log.d(TAG, "Started advertising as ${keyring.nodeId}")
        }.addOnFailureListener { e ->
            _isAdvertising.value = false
            Log.e(TAG, "Advertising failed", e)
        }
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        _isAdvertising.value = false
    }

    fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            _isDiscovering.value = true
            Log.d(TAG, "Started discovery for $SERVICE_ID")
        }.addOnFailureListener { e ->
            _isDiscovering.value = false
            Log.e(TAG, "Discovery failed", e)
        }
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _isDiscovering.value = false
    }

    // Nearby Callbacks
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Discovered endpoint: $endpointId (${info.endpointName})")
            // Automatically request connection
            connectionsClient.requestConnection(
                keyring.nodeId,
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                Log.e(TAG, "Connection request failed to $endpointId", e)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated from: $endpointId (${connectionInfo.endpointName})")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                Log.d(TAG, "Connection successful to endpoint: $endpointId")
                sendHandshakeAnnounce(endpointId)
                updateActiveLinks()
            } else {
                Log.w(TAG, "Connection failed to endpoint: $endpointId")
                handleDisconnect(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from endpoint: $endpointId")
            handleDisconnect(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    scope.launch {
                        processIncomingBytes(endpointId, bytes)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Transfer progress tracking
        }
    }

    private fun handleDisconnect(endpointId: String) {
        val nodeId = endpointToPeerNodeId.remove(endpointId)
        _connectedEndpoints.update { current ->
            current.filterKeys { it != endpointId }
        }
        updateActiveLinks()
        if (nodeId != null) {
            scope.launch {
                database.peerDao().updateConnectionStatus(
                    nodeId = nodeId,
                    connected = false,
                    endpointId = null,
                    lastSeen = System.currentTimeMillis()
                )
            }
        }
    }

    private fun updateActiveLinks() {
        _connectedEndpoints.value = endpointToPeerNodeId.toMap()
        _stats.update { it.copy(activeLinks = endpointToPeerNodeId.size) }
    }

    /**
     * Sends local public identity handshake to newly connected endpoint.
     */
    private fun sendHandshakeAnnounce(targetEndpointId: String) {
        val identity = keyring.toIdentityPayload()
        val identityJson = identity.toJson()

        // Sign the identity json with Ed25519
        val signature = CryptoManager.sign(keyring.ed25519PrivateKey, identityJson.toByteArray(Charsets.UTF_8))

        val packet = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            type = PacketType.HANDSHAKE_ANNOUNCE,
            sourceNodeId = keyring.nodeId,
            destinationId = "*",
            encryptedPayloadBase64 = Base64.encodeToString(identityJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP),
            ivBase64 = "",
            ed25519SignatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP),
            hopCount = 0,
            maxHops = 1
        )

        connectionsClient.sendPayload(targetEndpointId, Payload.fromBytes(packet.toByteArray()))
    }

    /**
     * Ingests and routes incoming packets according to cryptographic verification & destination.
     */
    private suspend fun processIncomingBytes(fromEndpointId: String, bytes: ByteArray) {
        val packet = MeshPacket.fromByteArray(bytes) ?: return

        // 1. Loop Prevention: Drop if previously seen
        synchronized(seenPacketIds) {
            if (!seenPacketIds.add(packet.packetId)) {
                return // Already processed
            }
        }

        _stats.update { it.copy(packetsReceived = it.packetsReceived + 1) }

        // 2. Handle Handshake
        if (packet.type == PacketType.HANDSHAKE_ANNOUNCE) {
            handleHandshakePacket(fromEndpointId, packet)
            return
        }

        // 3. Handle Direct Messages
        if (packet.type == PacketType.DIRECT_MESSAGE) {
            if (packet.destinationId == keyring.nodeId) {
                // For this node! Decrypt and persist
                handleDirectMessage(packet)
            } else if (packet.hopCount < packet.maxHops) {
                // Multi-hop routing forwarding
                forwardMeshPacket(packet, excludeEndpointId = fromEndpointId)
            }
            return
        }

        // 4. Handle Group Key Invite
        if (packet.type == PacketType.GROUP_KEY_INVITE) {
            if (packet.destinationId == keyring.nodeId) {
                handleGroupKeyInvite(packet)
            } else if (packet.hopCount < packet.maxHops) {
                forwardMeshPacket(packet, excludeEndpointId = fromEndpointId)
            }
            return
        }

        // 5. Handle Group Broadcast Messages
        if (packet.type == PacketType.GROUP_MESSAGE) {
            // Attempt to decrypt if node belongs to this group
            handleGroupMessage(packet)
            // Forward across mesh to other nodes (flooding algorithm)
            if (packet.hopCount < packet.maxHops) {
                forwardMeshPacket(packet, excludeEndpointId = fromEndpointId)
            }
            return
        }
    }

    private suspend fun handleHandshakePacket(fromEndpointId: String, packet: MeshPacket) {
        try {
            val jsonBytes = Base64.decode(packet.encryptedPayloadBase64, Base64.NO_WRAP)
            val jsonStr = String(jsonBytes, Charsets.UTF_8)
            val identity = IdentityPayload.fromJson(jsonStr) ?: return

            // Verify Ed25519 signature
            val sigBytes = Base64.decode(packet.ed25519SignatureBase64, Base64.NO_WRAP)
            val ed25519Pub = identity.ed25519PublicKeyHex.decodeHex()
            val valid = CryptoManager.verifySignature(ed25519Pub, jsonBytes, sigBytes)
            if (!valid) {
                Log.w(TAG, "Invalid handshake signature from ${identity.nodeId}")
                return
            }

            // Derive pairwise shared secret using local X25519 private key & peer's X25519 public key
            val peerX25519Pub = identity.x25519PublicKeyHex.decodeHex()
            val sharedKey = CryptoManager.deriveSharedKey(
                localX25519PrivateKey = keyring.x25519PrivateKey,
                peerX25519PublicKey = peerX25519Pub
            )

            val peerEntity = PeerEntity(
                nodeId = identity.nodeId,
                alias = identity.alias,
                x25519PublicKey = identity.x25519PublicKeyHex,
                ed25519PublicKey = identity.ed25519PublicKeyHex,
                lastSeen = System.currentTimeMillis(),
                isNearbyConnected = true,
                nearbyEndpointId = fromEndpointId,
                sharedSecretHex = sharedKey.toHex()
            )
            database.peerDao().upsertPeer(peerEntity)
            endpointToPeerNodeId[fromEndpointId] = identity.nodeId
            updateActiveLinks()
            Log.d(TAG, "Handshake successful with peer ${identity.nodeId} (${identity.alias})")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling handshake", e)
        }
    }

    private suspend fun handleDirectMessage(packet: MeshPacket) {
        val peer = database.peerDao().getPeerByNodeId(packet.sourceNodeId) ?: return
        val sharedSecretHex = peer.sharedSecretHex ?: return
        val sharedKey = sharedSecretHex.decodeHex()

        // 1. Verify Ed25519 signature
        val signable = packet.getSignableData()
        val sigBytes = Base64.decode(packet.ed25519SignatureBase64, Base64.NO_WRAP)
        val validSig = CryptoManager.verifySignature(peer.ed25519PublicKey.decodeHex(), signable, sigBytes)

        if (!validSig) {
            Log.w(TAG, "Dropped packet: signature verification failed from ${packet.sourceNodeId}")
            return
        }

        // 2. Decrypt AES-256-GCM payload
        try {
            val iv = Base64.decode(packet.ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(packet.encryptedPayloadBase64, Base64.NO_WRAP)
            val decryptedBytes = CryptoManager.decryptAesGcm(
                aesKey = sharedKey,
                iv = iv,
                ciphertextWithTag = cipherBytes,
                associatedData = packet.packetId.toByteArray(Charsets.UTF_8)
            )
            val messageText = String(decryptedBytes, Charsets.UTF_8)

            val msg = MessageEntity(
                messageId = packet.packetId,
                senderNodeId = packet.sourceNodeId,
                recipientId = keyring.nodeId,
                groupId = null,
                content = messageText,
                timestamp = packet.timestamp,
                isOutgoing = false,
                isVerified = true,
                status = "DELIVERED",
                hops = packet.hopCount
            )
            database.messageDao().insertMessage(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting direct message", e)
        }
    }

    private suspend fun handleGroupKeyInvite(packet: MeshPacket) {
        val peer = database.peerDao().getPeerByNodeId(packet.sourceNodeId) ?: return
        val sharedSecretHex = peer.sharedSecretHex ?: return
        val sharedKey = sharedSecretHex.decodeHex()

        // Verify signature
        val signable = packet.getSignableData()
        val sigBytes = Base64.decode(packet.ed25519SignatureBase64, Base64.NO_WRAP)
        if (!CryptoManager.verifySignature(peer.ed25519PublicKey.decodeHex(), signable, sigBytes)) {
            return
        }

        try {
            val iv = Base64.decode(packet.ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(packet.encryptedPayloadBase64, Base64.NO_WRAP)
            val decrypted = CryptoManager.decryptAesGcm(sharedKey, iv, cipherBytes, packet.packetId.toByteArray())
            val json = JSONObject(String(decrypted, Charsets.UTF_8))

            val groupId = json.getString("groupId")
            val groupName = json.getString("groupName")
            val groupKeyHex = json.getString("groupKeyHex")
            val membersCsv = json.optString("members", "")

            val group = GroupEntity(
                groupId = groupId,
                groupName = groupName,
                creatorNodeId = packet.sourceNodeId,
                groupKeyHex = groupKeyHex,
                memberNodeIdsCsv = membersCsv,
                createdAt = System.currentTimeMillis()
            )
            database.groupDao().upsertGroup(group)
            Log.d(TAG, "Received & decrypted group key for: $groupName ($groupId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting group key invite", e)
        }
    }

    private suspend fun handleGroupMessage(packet: MeshPacket) {
        val group = database.groupDao().getGroupById(packet.destinationId) ?: return
        val groupKey = group.groupKeyHex.decodeHex()

        // Verify sender signature if known
        val senderPeer = database.peerDao().getPeerByNodeId(packet.sourceNodeId)
        var isVerified = false
        if (senderPeer != null) {
            val signable = packet.getSignableData()
            val sigBytes = Base64.decode(packet.ed25519SignatureBase64, Base64.NO_WRAP)
            isVerified = CryptoManager.verifySignature(senderPeer.ed25519PublicKey.decodeHex(), signable, sigBytes)
        }

        try {
            val iv = Base64.decode(packet.ivBase64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(packet.encryptedPayloadBase64, Base64.NO_WRAP)
            val decrypted = CryptoManager.decryptAesGcm(groupKey, iv, cipherBytes, packet.packetId.toByteArray())
            val messageText = String(decrypted, Charsets.UTF_8)

            // Don't re-save if we sent it ourselves
            if (packet.sourceNodeId != keyring.nodeId) {
                val msg = MessageEntity(
                    messageId = packet.packetId,
                    senderNodeId = packet.sourceNodeId,
                    recipientId = packet.destinationId,
                    groupId = packet.destinationId,
                    content = messageText,
                    timestamp = packet.timestamp,
                    isOutgoing = false,
                    isVerified = isVerified,
                    status = "DELIVERED",
                    hops = packet.hopCount
                )
                database.messageDao().insertMessage(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt group message", e)
        }
    }

    private fun forwardMeshPacket(packet: MeshPacket, excludeEndpointId: String) {
        val forwardedPacket = packet.copy(hopCount = packet.hopCount + 1)
        val payload = Payload.fromBytes(forwardedPacket.toByteArray())

        endpointToPeerNodeId.keys.forEach { endpointId ->
            if (endpointId != excludeEndpointId) {
                connectionsClient.sendPayload(endpointId, payload)
            }
        }
        _stats.update { it.copy(packetsRouted = it.packetsRouted + 1) }
    }

    /**
     * Sends an End-to-End Encrypted Direct Message to a peer node.
     */
    suspend fun sendDirectMessage(recipientNodeId: String, text: String): Boolean {
        val peer = database.peerDao().getPeerByNodeId(recipientNodeId) ?: return false
        val sharedSecretHex = peer.sharedSecretHex ?: return false
        val sharedKey = sharedSecretHex.decodeHex()

        val packetId = UUID.randomUUID().toString()
        val (iv, ciphertext) = CryptoManager.encryptAesGcm(
            aesKey = sharedKey,
            plaintext = text.toByteArray(Charsets.UTF_8),
            associatedData = packetId.toByteArray(Charsets.UTF_8)
        )

        val packetDraft = MeshPacket(
            packetId = packetId,
            type = PacketType.DIRECT_MESSAGE,
            sourceNodeId = keyring.nodeId,
            destinationId = recipientNodeId,
            encryptedPayloadBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ed25519SignatureBase64 = "",
            hopCount = 0,
            maxHops = 5
        )

        // Sign packet
        val sig = CryptoManager.sign(keyring.ed25519PrivateKey, packetDraft.getSignableData())
        val finalPacket = packetDraft.copy(
            ed25519SignatureBase64 = Base64.encodeToString(sig, Base64.NO_WRAP)
        )

        // Record locally
        val entity = MessageEntity(
            messageId = packetId,
            senderNodeId = keyring.nodeId,
            recipientId = recipientNodeId,
            groupId = null,
            content = text,
            timestamp = System.currentTimeMillis(),
            isOutgoing = true,
            isVerified = true,
            status = "SENT",
            hops = 0
        )
        database.messageDao().insertMessage(entity)

        // Broadcast to all connected endpoints (direct or mesh flood)
        val payload = Payload.fromBytes(finalPacket.toByteArray())
        endpointToPeerNodeId.keys.forEach { endpointId ->
            connectionsClient.sendPayload(endpointId, payload)
        }

        _stats.update { it.copy(packetsSent = it.packetsSent + 1) }
        return true
    }

    /**
     * Creates dynamic group and distributes symmetric group key to all members individually.
     */
    suspend fun createGroupAndDistributeKey(groupName: String, memberNodeIds: List<String>): String {
        val groupId = "grp-${UUID.randomUUID().toString().take(8)}"
        val groupKey = CryptoManager.generateSymmetricKey()
        val allMembers = (memberNodeIds + keyring.nodeId).distinct()

        val groupEntity = GroupEntity(
            groupId = groupId,
            groupName = groupName,
            creatorNodeId = keyring.nodeId,
            groupKeyHex = groupKey.toHex(),
            memberNodeIdsCsv = allMembers.joinToString(","),
            createdAt = System.currentTimeMillis()
        )
        database.groupDao().upsertGroup(groupEntity)

        // Distribute encrypted group key individually to each member
        memberNodeIds.forEach { memberId ->
            val peer = database.peerDao().getPeerByNodeId(memberId)
            val sharedSecretHex = peer?.sharedSecretHex
            if (sharedSecretHex != null) {
                val memberSharedKey = sharedSecretHex.decodeHex()
                val inviteObj = JSONObject()
                inviteObj.put("groupId", groupId)
                inviteObj.put("groupName", groupName)
                inviteObj.put("groupKeyHex", groupKey.toHex())
                inviteObj.put("members", allMembers.joinToString(","))

                val packetId = UUID.randomUUID().toString()
                val (iv, ciphertext) = CryptoManager.encryptAesGcm(
                    aesKey = memberSharedKey,
                    plaintext = inviteObj.toString().toByteArray(Charsets.UTF_8),
                    associatedData = packetId.toByteArray()
                )

                val packetDraft = MeshPacket(
                    packetId = packetId,
                    type = PacketType.GROUP_KEY_INVITE,
                    sourceNodeId = keyring.nodeId,
                    destinationId = memberId,
                    encryptedPayloadBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                    ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                    ed25519SignatureBase64 = "",
                    hopCount = 0,
                    maxHops = 5
                )
                val sig = CryptoManager.sign(keyring.ed25519PrivateKey, packetDraft.getSignableData())
                val finalPacket = packetDraft.copy(
                    ed25519SignatureBase64 = Base64.encodeToString(sig, Base64.NO_WRAP)
                )

                val payload = Payload.fromBytes(finalPacket.toByteArray())
                endpointToPeerNodeId.keys.forEach { ep ->
                    connectionsClient.sendPayload(ep, payload)
                }
            }
        }

        return groupId
    }

    /**
     * Broadcasts an encrypted group message over the mesh.
     */
    suspend fun sendGroupMessage(groupId: String, text: String): Boolean {
        val group = database.groupDao().getGroupById(groupId) ?: return false
        val groupKey = group.groupKeyHex.decodeHex()

        val packetId = UUID.randomUUID().toString()
        val (iv, ciphertext) = CryptoManager.encryptAesGcm(
            aesKey = groupKey,
            plaintext = text.toByteArray(Charsets.UTF_8),
            associatedData = packetId.toByteArray()
        )

        val packetDraft = MeshPacket(
            packetId = packetId,
            type = PacketType.GROUP_MESSAGE,
            sourceNodeId = keyring.nodeId,
            destinationId = groupId,
            encryptedPayloadBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ed25519SignatureBase64 = "",
            hopCount = 0,
            maxHops = 5
        )

        val sig = CryptoManager.sign(keyring.ed25519PrivateKey, packetDraft.getSignableData())
        val finalPacket = packetDraft.copy(
            ed25519SignatureBase64 = Base64.encodeToString(sig, Base64.NO_WRAP)
        )

        // Save locally
        val entity = MessageEntity(
            messageId = packetId,
            senderNodeId = keyring.nodeId,
            recipientId = groupId,
            groupId = groupId,
            content = text,
            timestamp = System.currentTimeMillis(),
            isOutgoing = true,
            isVerified = true,
            status = "SENT",
            hops = 0
        )
        database.messageDao().insertMessage(entity)

        // Flood to all mesh neighbors
        val payload = Payload.fromBytes(finalPacket.toByteArray())
        endpointToPeerNodeId.keys.forEach { ep ->
            connectionsClient.sendPayload(ep, payload)
        }

        _stats.update { it.copy(packetsSent = it.packetsSent + 1) }
        return true
    }

    /**
     * Manually pairs a peer from a scanned/pasted QR code payload.
     */
    suspend fun pairPeerFromIdentityPayload(payload: IdentityPayload): Boolean {
        if (payload.nodeId == keyring.nodeId) return false

        val peerX25519Pub = payload.x25519PublicKeyHex.decodeHex()
        val sharedKey = CryptoManager.deriveSharedKey(
            localX25519PrivateKey = keyring.x25519PrivateKey,
            peerX25519PublicKey = peerX25519Pub
        )

        val peerEntity = PeerEntity(
            nodeId = payload.nodeId,
            alias = payload.alias,
            x25519PublicKey = payload.x25519PublicKeyHex,
            ed25519PublicKey = payload.ed25519PublicKeyHex,
            lastSeen = System.currentTimeMillis(),
            isNearbyConnected = false,
            nearbyEndpointId = null,
            sharedSecretHex = sharedKey.toHex()
        )
        database.peerDao().upsertPeer(peerEntity)
        return true
    }
}
