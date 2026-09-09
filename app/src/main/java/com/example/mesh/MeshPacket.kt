package com.example.mesh

import android.util.Base64
import org.json.JSONObject

enum class PacketType {
    HANDSHAKE_ANNOUNCE, // Node exchanges NodeId, X25519 PK, Ed25519 PK, Alias
    DIRECT_MESSAGE,     // Encrypted with derived pairwise HKDF AES-256-GCM
    GROUP_KEY_INVITE,   // Encrypted symmetric group key distributed to a member
    GROUP_MESSAGE,      // Broadcast message encrypted with group symmetric AES-256-GCM
    ROUTING_HEARTBEAT,  // Topology broadcast
    SOS_BEACON          // Emergency priority broadcast beacon (extended TTL flood)
}

data class MeshPacket(
    val packetId: String,
    val type: PacketType,
    val sourceNodeId: String,
    val destinationId: String, // Destination Node ID, Group ID, or "*" for broadcast
    val encryptedPayloadBase64: String,
    val ivBase64: String,
    val ed25519SignatureBase64: String,
    val hopCount: Int = 0,
    val maxHops: Int = 5,
    val timestamp: Long = System.currentTimeMillis(),
    val ephemeralDurationMs: Long? = null
) {
    /**
     * Canonical string representation for cryptographic signing and verification.
     */
    fun getSignableData(): ByteArray {
        val canonical = "$packetId|$type|$sourceNodeId|$destinationId|$encryptedPayloadBase64|$ivBase64|$timestamp|${ephemeralDurationMs ?: 0}"
        return canonical.toByteArray(Charsets.UTF_8)
    }

    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("pid", packetId)
        obj.put("type", type.name)
        obj.put("src", sourceNodeId)
        obj.put("dst", destinationId)
        obj.put("payload", encryptedPayloadBase64)
        obj.put("iv", ivBase64)
        obj.put("sig", ed25519SignatureBase64)
        obj.put("hops", hopCount)
        obj.put("maxHops", maxHops)
        obj.put("ts", timestamp)
        if (ephemeralDurationMs != null) {
            obj.put("eph", ephemeralDurationMs)
        }
        return obj.toString()
    }

    fun toByteArray(): ByteArray {
        return toJsonString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): MeshPacket? {
            return try {
                val json = String(bytes, Charsets.UTF_8)
                val obj = JSONObject(json)
                val eph = if (obj.has("eph")) obj.getLong("eph") else null
                MeshPacket(
                    packetId = obj.getString("pid"),
                    type = PacketType.valueOf(obj.getString("type")),
                    sourceNodeId = obj.getString("src"),
                    destinationId = obj.getString("dst"),
                    encryptedPayloadBase64 = obj.getString("payload"),
                    ivBase64 = obj.getString("iv"),
                    ed25519SignatureBase64 = obj.getString("sig"),
                    hopCount = obj.optInt("hops", 0),
                    maxHops = obj.optInt("maxHops", 5),
                    timestamp = obj.optLong("ts", System.currentTimeMillis()),
                    ephemeralDurationMs = eph
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

