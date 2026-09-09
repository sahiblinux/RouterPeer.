package com.example.crypto

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {

    private val secureRandom = SecureRandom()
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 12000
    private const val PBKDF2_KEY_LENGTH = 256

    /**
     * Generates a unique, cryptographically secure 8-digit identification number
     * formatted as e.g. 4930-2948 using java.security.SecureRandom.
     */
    fun generateNodeId(): String {
        val num1 = secureRandom.nextInt(9000) + 1000
        val num2 = secureRandom.nextInt(9000) + 1000
        return "$num1-$num2"
    }

    /**
     * Generates a random 256-bit symmetric key for dynamic group chats.
     */
    fun generateSymmetricKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * Generates a complete cryptographic keyring (X25519 for ECDH + Ed25519 for packet signing).
     */
    fun generateKeyring(nodeId: String, alias: String): LocalKeyring {
        // 1. Generate X25519 Key Pair
        val xGen = X25519KeyPairGenerator()
        xGen.init(X25519KeyGenerationParameters(secureRandom))
        val xPair = xGen.generateKeyPair()
        val xPriv = (xPair.private as X25519PrivateKeyParameters).encoded
        val xPub = (xPair.public as X25519PublicKeyParameters).encoded

        // 2. Generate Ed25519 Key Pair
        val edGen = Ed25519KeyPairGenerator()
        edGen.init(Ed25519KeyGenerationParameters(secureRandom))
        val edPair = edGen.generateKeyPair()
        val edPriv = (edPair.private as Ed25519PrivateKeyParameters).encoded
        val edPub = (edPair.public as Ed25519PublicKeyParameters).encoded

        return LocalKeyring(
            nodeId = nodeId,
            alias = alias,
            x25519PrivateKey = xPriv,
            x25519PublicKey = xPub,
            ed25519PrivateKey = edPriv,
            ed25519PublicKey = edPub
        )
    }

    /**
     * Computes the ECDH shared secret using X25519 and derives a 256-bit AES symmetric key via HKDF.
     */
    fun deriveSharedKey(
        localX25519PrivateKey: ByteArray,
        peerX25519PublicKey: ByteArray,
        contextInfo: String = "RouterPeer-P2P-HKDF-v1"
    ): ByteArray {
        // 1. X25519 Agreement
        val agreement = X25519Agreement()
        val privateParams = X25519PrivateKeyParameters(localX25519PrivateKey, 0)
        val publicParams = X25519PublicKeyParameters(peerX25519PublicKey, 0)
        agreement.init(privateParams)

        val rawSharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(publicParams, rawSharedSecret, 0)

        // 2. HKDF-SHA256 Derivation
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        val hkdfParams = HKDFParameters(
            rawSharedSecret,
            "RouterPeerSalt".toByteArray(Charsets.UTF_8),
            contextInfo.toByteArray(Charsets.UTF_8)
        )
        hkdf.init(hkdfParams)

        val derivedKey = ByteArray(32) // 256-bit AES key
        hkdf.generateBytes(derivedKey, 0, 32)
        return derivedKey
    }

    /**
     * Signs data using Ed25519.
     */
    fun sign(ed25519PrivateKey: ByteArray, data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(ed25519PrivateKey, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    /**
     * Verifies an Ed25519 signature against data.
     */
    fun verifySignature(ed25519PublicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(ed25519PublicKey, 0))
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Returns Pair(iv: ByteArray, ciphertextWithTag: ByteArray).
     */
    fun encryptAesGcm(
        aesKey: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(aesKey, "AES")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val ciphertext = cipher.doFinal(plaintext)
        return Pair(iv, ciphertext)
    }

    /**
     * Decrypts ciphertext using AES-256-GCM and verifies authenticity tag.
     */
    fun decryptAesGcm(
        aesKey: ByteArray,
        iv: ByteArray,
        ciphertextWithTag: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(aesKey, "AES")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(ciphertextWithTag)
    }

    /**
     * Hashes password using PBKDF2 with SHA-256 and unique salt.
     */
    fun hashPassword(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a high-contrast QR code Bitmap for the given payload string.
     */
    fun generateHighContrastQrCode(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val darkPixel = Color.BLACK
        val lightPixel = Color.WHITE

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) darkPixel else lightPixel)
            }
        }
        return bitmap
    }
}
