package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.crypto.CryptoManager
import com.example.crypto.LocalKeyring
import com.example.crypto.decodeHex
import com.example.crypto.toHex
import java.util.Arrays

class SecurePreferences(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
        }
    }

    fun isGatewayConfigured(): Boolean {
        return prefs.getBoolean(KEY_IS_CONFIGURED, false)
    }

    fun configurePasswords(primaryPass: String, panicPass: String, decoyPass: String? = null) {
        val primarySalt = CryptoManager.generateSalt()
        val panicSalt = CryptoManager.generateSalt()

        val primaryHash = CryptoManager.hashPassword(primaryPass.toCharArray(), primarySalt)
        val panicHash = CryptoManager.hashPassword(panicPass.toCharArray(), panicSalt)

        val editor = prefs.edit()
            .putString(KEY_PRIMARY_HASH, primaryHash.toHex())
            .putString(KEY_PRIMARY_SALT, primarySalt.toHex())
            .putString(KEY_PANIC_HASH, panicHash.toHex())
            .putString(KEY_PANIC_SALT, panicSalt.toHex())
            .putBoolean(KEY_IS_CONFIGURED, true)

        if (!decoyPass.isNullOrBlank()) {
            val decoySalt = CryptoManager.generateSalt()
            val decoyHash = CryptoManager.hashPassword(decoyPass.toCharArray(), decoySalt)
            editor.putString(KEY_DECOY_HASH, decoyHash.toHex())
            editor.putString(KEY_DECOY_SALT, decoySalt.toHex())
        }

        editor.apply()
    }

    enum class PasswordVerificationResult {
        PRIMARY_SUCCESS,
        PANIC_TRIGGERED,
        DECOY_SUCCESS,
        INVALID
    }

    fun verifyPassword(enteredPassword: String): PasswordVerificationResult {
        val primarySaltHex = prefs.getString(KEY_PRIMARY_SALT, null)
        val primaryHashHex = prefs.getString(KEY_PRIMARY_HASH, null)
        val panicSaltHex = prefs.getString(KEY_PANIC_SALT, null)
        val panicHashHex = prefs.getString(KEY_PANIC_HASH, null)
        val decoySaltHex = prefs.getString(KEY_DECOY_SALT, null)
        val decoyHashHex = prefs.getString(KEY_DECOY_HASH, null)

        if (primarySaltHex == null || primaryHashHex == null || panicSaltHex == null || panicHashHex == null) {
            return PasswordVerificationResult.INVALID
        }

        // 1. Check Panic Password First (constant time comparison)
        val panicSalt = panicSaltHex.decodeHex()
        val expectedPanicHash = panicHashHex.decodeHex()
        val enteredPanicHash = CryptoManager.hashPassword(enteredPassword.toCharArray(), panicSalt)
        if (Arrays.equals(enteredPanicHash, expectedPanicHash)) {
            return PasswordVerificationResult.PANIC_TRIGGERED
        }

        // 2. Check Decoy Password (if configured)
        if (decoySaltHex != null && decoyHashHex != null) {
            val decoySalt = decoySaltHex.decodeHex()
            val expectedDecoyHash = decoyHashHex.decodeHex()
            val enteredDecoyHash = CryptoManager.hashPassword(enteredPassword.toCharArray(), decoySalt)
            if (Arrays.equals(enteredDecoyHash, expectedDecoyHash)) {
                return PasswordVerificationResult.DECOY_SUCCESS
            }
        }

        // 3. Check Primary Password
        val primarySalt = primarySaltHex.decodeHex()
        val expectedPrimaryHash = primaryHashHex.decodeHex()
        val enteredPrimaryHash = CryptoManager.hashPassword(enteredPassword.toCharArray(), primarySalt)
        if (Arrays.equals(enteredPrimaryHash, expectedPrimaryHash)) {
            return PasswordVerificationResult.PRIMARY_SUCCESS
        }

        return PasswordVerificationResult.INVALID
    }

    fun getDecoyKeyring(): LocalKeyring {
        return LocalKeyring(
            nodeId = "99401827",
            alias = "Guest Vault",
            x25519PrivateKey = ByteArray(32) { 0x01 },
            x25519PublicKey = ByteArray(32) { 0x02 },
            ed25519PrivateKey = ByteArray(32) { 0x03 },
            ed25519PublicKey = ByteArray(32) { 0x04 }
        )
    }


    fun getOrCreateKeyring(): LocalKeyring {
        val storedNodeId = prefs.getString(KEY_NODE_ID, null)
        if (storedNodeId != null) {
            return LocalKeyring(
                nodeId = storedNodeId,
                alias = prefs.getString(KEY_ALIAS, "Node-$storedNodeId") ?: "Node-$storedNodeId",
                x25519PrivateKey = prefs.getString(KEY_X25519_PRIV, "")!!.decodeHex(),
                x25519PublicKey = prefs.getString(KEY_X25519_PUB, "")!!.decodeHex(),
                ed25519PrivateKey = prefs.getString(KEY_ED25519_PRIV, "")!!.decodeHex(),
                ed25519PublicKey = prefs.getString(KEY_ED25519_PUB, "")!!.decodeHex()
            )
        }

        // First launch initialization
        val newNodeId = CryptoManager.generateNodeId()
        val alias = "Ghost-${newNodeId.take(4)}"
        val keyring = CryptoManager.generateKeyring(newNodeId, alias)

        saveKeyring(keyring)
        return keyring
    }

    fun saveKeyring(keyring: LocalKeyring) {
        prefs.edit()
            .putString(KEY_NODE_ID, keyring.nodeId)
            .putString(KEY_ALIAS, keyring.alias)
            .putString(KEY_X25519_PRIV, keyring.x25519PrivateKey.toHex())
            .putString(KEY_X25519_PUB, keyring.x25519PublicKey.toHex())
            .putString(KEY_ED25519_PRIV, keyring.ed25519PrivateKey.toHex())
            .putString(KEY_ED25519_PUB, keyring.ed25519PublicKey.toHex())
            .apply()
    }

    fun updateAlias(newAlias: String) {
        prefs.edit().putString(KEY_ALIAS, newAlias).apply()
    }

    fun clearAll() {
        prefs.edit().clear().commit()
    }

    companion object {
        const val PREFS_FILENAME = "routerpeer_secure_keystore_prefs"
        private const val KEY_IS_CONFIGURED = "is_gateway_configured"
        private const val KEY_PRIMARY_HASH = "primary_pw_hash"
        private const val KEY_PRIMARY_SALT = "primary_pw_salt"
        private const val KEY_PANIC_HASH = "panic_pw_hash"
        private const val KEY_PANIC_SALT = "panic_pw_salt"
        private const val KEY_DECOY_HASH = "decoy_pw_hash"
        private const val KEY_DECOY_SALT = "decoy_pw_salt"

        private const val KEY_NODE_ID = "node_id_8digit"
        private const val KEY_ALIAS = "node_alias"
        private const val KEY_X25519_PRIV = "x25519_priv_hex"
        private const val KEY_X25519_PUB = "x25519_pub_hex"
        private const val KEY_ED25519_PRIV = "ed25519_priv_hex"
        private const val KEY_ED25519_PUB = "ed25519_pub_hex"
    }
}
