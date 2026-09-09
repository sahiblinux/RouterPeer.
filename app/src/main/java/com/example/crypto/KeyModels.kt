package com.example.crypto

import org.json.JSONObject

/**
 * Public Identity payload shared via QR code or Nearby initial handshake.
 * Strictly contains no personal data (zero-data architecture).
 */
data class IdentityPayload(
    val nodeId: String,
    val x25519PublicKeyHex: String,
    val ed25519PublicKeyHex: String,
    val alias: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("nodeId", nodeId)
        obj.put("x25519Pk", x25519PublicKeyHex)
        obj.put("ed25519Pk", ed25519PublicKeyHex)
        obj.put("alias", alias)
        obj.put("ts", timestamp)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): IdentityPayload? {
            return try {
                val obj = JSONObject(jsonStr)
                IdentityPayload(
                    nodeId = obj.getString("nodeId"),
                    x25519PublicKeyHex = obj.getString("x25519Pk"),
                    ed25519PublicKeyHex = obj.getString("ed25519Pk"),
                    alias = obj.optString("alias", "Peer-${obj.getString("nodeId").takeLast(4)}"),
                    timestamp = obj.optLong("ts", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Key pairs generated and stored in EncryptedSharedPreferences.
 */
data class LocalKeyring(
    val nodeId: String,
    val alias: String,
    val x25519PrivateKey: ByteArray,
    val x25519PublicKey: ByteArray,
    val ed25519PrivateKey: ByteArray,
    val ed25519PublicKey: ByteArray
) {
    fun toIdentityPayload(): IdentityPayload {
        return IdentityPayload(
            nodeId = nodeId,
            x25519PublicKeyHex = x25519PublicKey.toHex(),
            ed25519PublicKeyHex = ed25519PublicKey.toHex(),
            alias = alias
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LocalKeyring
        return nodeId == other.nodeId
    }

    override fun hashCode(): Int = nodeId.hashCode()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
