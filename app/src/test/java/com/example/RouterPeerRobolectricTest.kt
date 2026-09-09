package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoManager
import com.example.crypto.decodeHex
import com.example.crypto.toHex
import com.example.data.SecurePreferences
import com.example.data.db.AppDatabase
import com.example.data.db.GroupEntity
import com.example.data.db.MessageEntity
import com.example.data.db.PeerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RouterPeerRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun testNodeIdFormatAndUniqueness() {
        val id1 = CryptoManager.generateNodeId()
        val id2 = CryptoManager.generateNodeId()

        // Formatted as XXXX-XXXX (8 digits with dash)
        val regex = Regex("""^\d{4}-\d{4}$""")
        assertTrue("Node ID $id1 must match 8-digit pattern", regex.matches(id1))
        assertTrue("Node ID $id2 must match 8-digit pattern", regex.matches(id2))
        assertFalse("Two generated Node IDs should not collide", id1 == id2)
    }

    @Test
    fun testEndToEndEncryptionAndSignatureFlow() {
        // 1. Generate Keyrings for Node A and Node B
        val keyringA = CryptoManager.generateKeyring(nodeId = "1001-2002", alias = "GhostAlpha")
        val keyringB = CryptoManager.generateKeyring(nodeId = "3003-4004", alias = "GhostBeta")

        // 2. Both nodes independently derive pairwise shared symmetric key via ECDH (X25519) + HKDF-SHA256
        val sharedKeyA = CryptoManager.deriveSharedKey(
            localX25519PrivateKey = keyringA.x25519PrivateKey,
            peerX25519PublicKey = keyringB.x25519PublicKey
        )
        val sharedKeyB = CryptoManager.deriveSharedKey(
            localX25519PrivateKey = keyringB.x25519PrivateKey,
            peerX25519PublicKey = keyringA.x25519PublicKey
        )

        assertEquals("Shared keys derived via ECDH + HKDF must match identically", sharedKeyA.toHex(), sharedKeyB.toHex())

        // 3. Node A encrypts confidential message with AES-256-GCM
        val originalMessage = "Top Secret Mesh Packet: Coordinates 37.7749, -122.4194"
        val packetId = UUID.randomUUID().toString()
        val (iv, ciphertext) = CryptoManager.encryptAesGcm(
            aesKey = sharedKeyA,
            plaintext = originalMessage.toByteArray(Charsets.UTF_8),
            associatedData = packetId.toByteArray(Charsets.UTF_8)
        )

        // 4. Node A signs packet with Ed25519 private key
        val signature = CryptoManager.sign(keyringA.ed25519PrivateKey, ciphertext)

        // 5. Node B verifies Ed25519 signature using Node A's public key
        val isValidSig = CryptoManager.verifySignature(keyringA.ed25519PublicKey, ciphertext, signature)
        assertTrue("Ed25519 packet signature verification must succeed", isValidSig)

        // 6. Node B decrypts ciphertext using AES-256-GCM and verifies authenticity tag
        val decryptedBytes = CryptoManager.decryptAesGcm(
            aesKey = sharedKeyB,
            iv = iv,
            ciphertextWithTag = ciphertext,
            associatedData = packetId.toByteArray(Charsets.UTF_8)
        )
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals("Decrypted message must equal original plaintext", originalMessage, decryptedText)
    }

    @Test
    fun testHighContrastQrCodeGeneration() {
        val dummyPayload = "{\"nodeId\":\"4930-2948\",\"alias\":\"Ghost-4930\"}"
        val qrBitmap = CryptoManager.generateHighContrastQrCode(dummyPayload, size = 256)
        assertNotNull("Generated QR bitmap must not be null", qrBitmap)
        assertEquals(256, qrBitmap.width)
        assertEquals(256, qrBitmap.height)
    }

    @Test
    fun testDualPasswordVerification() {
        val securePrefs = SecurePreferences(context)
        val primary = "AlphaSecure123"
        val panic = "DuressTrigger999"

        securePrefs.configurePasswords(primary, panic)
        assertTrue(securePrefs.isGatewayConfigured())

        // Test Primary Password -> PRIMARY_SUCCESS
        val primaryResult = securePrefs.verifyPassword(primary)
        assertEquals(SecurePreferences.PasswordVerificationResult.PRIMARY_SUCCESS, primaryResult)

        // Test Panic Password -> PANIC_TRIGGERED
        val panicResult = securePrefs.verifyPassword(panic)
        assertEquals(SecurePreferences.PasswordVerificationResult.PANIC_TRIGGERED, panicResult)

        // Test Invalid Password -> INVALID
        val invalidResult = securePrefs.verifyPassword("WrongPassword!")
        assertEquals(SecurePreferences.PasswordVerificationResult.INVALID, invalidResult)
    }

    @Test
    fun testRoomDatabaseOperations() = runBlocking {
        val messageDao = database.messageDao()
        val peerDao = database.peerDao()
        val groupDao = database.groupDao()

        // Peer insertion
        val peer = PeerEntity(
            nodeId = "7777-8888",
            alias = "Vanguard",
            x25519PublicKey = "0102030405",
            ed25519PublicKey = "060708090a",
            isNearbyConnected = true
        )
        peerDao.upsertPeer(peer)

        val retrievedPeer = peerDao.getPeerByNodeId("7777-8888")
        assertNotNull(retrievedPeer)
        assertEquals("Vanguard", retrievedPeer?.alias)

        // Message insertion
        val message = MessageEntity(
            messageId = "msg-123",
            senderNodeId = "7777-8888",
            recipientId = "my-node-id",
            content = "Encrypted Hello over Mesh",
            isOutgoing = false,
            isVerified = true,
            status = "DELIVERED"
        )
        messageDao.insertMessage(message)

        val conversation = messageDao.getDirectConversation("7777-8888", "my-node-id").first()
        assertEquals(1, conversation.size)
        assertEquals("Encrypted Hello over Mesh", conversation.first().content)

        // Group insertion
        val group = GroupEntity(
            groupId = "grp-alpha",
            groupName = "Covert Squad",
            creatorNodeId = "my-node-id",
            groupKeyHex = "aabbccddeeff",
            memberNodeIdsCsv = "my-node-id,7777-8888"
        )
        groupDao.upsertGroup(group)

        val retrievedGroup = groupDao.getGroupById("grp-alpha")
        assertNotNull(retrievedGroup)
        assertEquals("Covert Squad", retrievedGroup?.groupName)
    }
}
