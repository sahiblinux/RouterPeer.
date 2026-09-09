package com.example.data.repository

import com.example.crypto.IdentityPayload
import com.example.crypto.LocalKeyring
import com.example.data.SecurePreferences
import com.example.data.db.AppDatabase
import com.example.data.db.GroupEntity
import com.example.data.db.MessageEntity
import com.example.data.db.PeerEntity
import com.example.mesh.MeshStats
import com.example.mesh.NearbyMeshManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class MeshRepository(
    private val database: AppDatabase,
    private val securePreferences: SecurePreferences,
    private val meshManager: NearbyMeshManager,
    val keyring: LocalKeyring
) {
    val allPeers: Flow<List<PeerEntity>> = database.peerDao().getAllPeers()
    val allGroups: Flow<List<GroupEntity>> = database.groupDao().getAllGroups()
    val allMessages: Flow<List<MessageEntity>> = database.messageDao().getAllMessages()
    val meshStats: StateFlow<MeshStats> = meshManager.stats
    val isAdvertising: StateFlow<Boolean> = meshManager.isAdvertising
    val isDiscovering: StateFlow<Boolean> = meshManager.isDiscovering
    val connectedEndpoints: StateFlow<Map<String, String>> = meshManager.connectedEndpoints
    val mulePacketCount: Flow<Int> = meshManager.mulePacketCount
    val acousticModem = meshManager.acousticModem

    fun getDirectConversation(peerNodeId: String): Flow<List<MessageEntity>> {
        return database.messageDao().getDirectConversation(peerNodeId, keyring.nodeId)
    }

    fun getGroupConversation(groupId: String): Flow<List<MessageEntity>> {
        return database.messageDao().getGroupConversation(groupId)
    }

    suspend fun sendDirectMessage(recipientNodeId: String, text: String, ephemeralDurationMs: Long? = null): Boolean {
        return meshManager.sendDirectMessage(recipientNodeId, text, ephemeralDurationMs)
    }

    suspend fun sendEmergencySosBeacon(distressType: String, notes: String): Boolean {
        return meshManager.sendEmergencySosBeacon(distressType, notes)
    }

    suspend fun sendGroupMessage(groupId: String, text: String): Boolean {
        return meshManager.sendGroupMessage(groupId, text)
    }

    suspend fun createGroup(groupName: String, memberNodeIds: List<String>): String {
        return meshManager.createGroupAndDistributeKey(groupName, memberNodeIds)
    }

    suspend fun pairPeer(payload: IdentityPayload): Boolean {
        return meshManager.pairPeerFromIdentityPayload(payload)
    }

    fun startMesh() {
        meshManager.startMesh()
    }

    fun stopMesh() {
        meshManager.stopMesh()
    }

    fun toggleAdvertising(enable: Boolean) {
        if (enable) meshManager.startAdvertising() else meshManager.stopAdvertising()
    }

    fun toggleDiscovery(enable: Boolean) {
        if (enable) meshManager.startDiscovery() else meshManager.stopDiscovery()
    }

    fun updateAlias(newAlias: String) {
        securePreferences.updateAlias(newAlias)
    }
}
