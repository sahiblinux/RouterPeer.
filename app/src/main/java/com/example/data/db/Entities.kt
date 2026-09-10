package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["messageId"], unique = true),
        Index(value = ["recipientId"]),
        Index(value = ["groupId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val senderNodeId: String,
    val recipientId: String, // Node ID or Group ID
    val groupId: String? = null,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean,
    val isVerified: Boolean = true,
    val status: String = "SENT", // PENDING, SENT, DELIVERED, ROUTED, EMERGENCY_SOS
    val hops: Int = 0,
    val ephemeralDurationMs: Long? = null,
    val expiresAt: Long? = null,
    val isScrubbed: Boolean = false,
    val mediaType: String = "TEXT", // TEXT, IMAGE, VIDEO, VOICE_NOTE
    val mediaUri: String? = null,
    val mediaSize: Long = 0L,
    val mediaDurationMs: Long = 0L
)

@Entity(
    tableName = "mule_packets",
    indices = [
        Index(value = ["packetId"], unique = true),
        Index(value = ["destinationId"]),
        Index(value = ["expiresAt"])
    ]
)
data class MulePacketEntity(
    @PrimaryKey
    val packetId: String,
    val destinationId: String, // Destination Node ID or Group ID
    val packetJson: String,
    val storedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (48 * 60 * 60 * 1000L), // 48h default TTL
    val forwardCount: Int = 0
)


@Entity(
    tableName = "peers",
    indices = [Index(value = ["nodeId"], unique = true)]
)
data class PeerEntity(
    @PrimaryKey
    val nodeId: String,
    val alias: String,
    val x25519PublicKey: String,
    val ed25519PublicKey: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val isNearbyConnected: Boolean = false,
    val nearbyEndpointId: String? = null,
    val sharedSecretHex: String? = null
)

@Entity(
    tableName = "mesh_groups",
    indices = [Index(value = ["groupId"], unique = true)]
)
data class GroupEntity(
    @PrimaryKey
    val groupId: String,
    val groupName: String,
    val creatorNodeId: String,
    val groupKeyHex: String, // 32-byte AES-256 group key in hex
    val memberNodeIdsCsv: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mesh_routes"
)
data class MeshRouteEntity(
    @PrimaryKey
    val destinationNodeId: String,
    val nextHopEndpointId: String,
    val hopCount: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
