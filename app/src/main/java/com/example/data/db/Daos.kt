package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE (senderNodeId = :nodeId AND recipientId = :myNodeId) OR (senderNodeId = :myNodeId AND recipientId = :nodeId) ORDER BY timestamp ASC")
    fun getDirectConversation(nodeId: String, myNodeId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getGroupConversation(groupId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :newStatus WHERE messageId = :messageId")
    suspend fun updateStatus(messageId: String, newStatus: String)

    @Query("SELECT * FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now AND isScrubbed = 0")
    suspend fun getExpiredUnscrubbedMessages(now: Long = System.currentTimeMillis()): List<MessageEntity>

    @Query("UPDATE messages SET content = '[PURGED - ZERO TRACE]', isScrubbed = 1 WHERE id = :id")
    suspend fun scrubMessage(id: Long)

    @Query("DELETE FROM messages WHERE isScrubbed = 1")
    suspend fun deleteScrubbedMessages()

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}


@Dao
interface PeerDao {

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getPeerByNodeId(nodeId: String): PeerEntity?

    @Query("SELECT * FROM peers WHERE nearbyEndpointId = :endpointId LIMIT 1")
    suspend fun getPeerByEndpointId(endpointId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: PeerEntity)

    @Query("UPDATE peers SET isNearbyConnected = :connected, nearbyEndpointId = :endpointId, lastSeen = :lastSeen WHERE nodeId = :nodeId")
    suspend fun updateConnectionStatus(nodeId: String, connected: Boolean, endpointId: String?, lastSeen: Long)

    @Query("UPDATE peers SET isNearbyConnected = 0, nearbyEndpointId = NULL")
    suspend fun markAllDisconnected()

    @Query("DELETE FROM peers WHERE nodeId = :nodeId")
    suspend fun deletePeer(nodeId: String)

    @Query("DELETE FROM peers")
    suspend fun deleteAll()
}

@Dao
interface GroupDao {

    @Query("SELECT * FROM mesh_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM mesh_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: GroupEntity)

    @Query("DELETE FROM mesh_groups WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM mesh_groups")
    suspend fun deleteAll()
}

@Dao
interface MeshRouteDao {

    @Query("SELECT * FROM mesh_routes")
    fun getAllRoutes(): Flow<List<MeshRouteEntity>>

    @Query("SELECT * FROM mesh_routes WHERE destinationNodeId = :destId LIMIT 1")
    suspend fun getRoute(destId: String): MeshRouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoute(route: MeshRouteEntity)

    @Query("DELETE FROM mesh_routes WHERE nextHopEndpointId = :endpointId")
    suspend fun removeRoutesViaEndpoint(endpointId: String)

    @Query("DELETE FROM mesh_routes")
    suspend fun deleteAll()
}

@Dao
interface MulePacketDao {

    @Query("SELECT * FROM mule_packets WHERE destinationId = :destId AND expiresAt > :now")
    suspend fun getPacketsForDestination(destId: String, now: Long = System.currentTimeMillis()): List<MulePacketEntity>

    @Query("SELECT * FROM mule_packets WHERE expiresAt > :now")
    suspend fun getAllValidMulePackets(now: Long = System.currentTimeMillis()): List<MulePacketEntity>

    @Query("SELECT COUNT(*) FROM mule_packets WHERE expiresAt > :now")
    fun getValidMuleCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMulePacket(packet: MulePacketEntity)

    @Query("DELETE FROM mule_packets WHERE packetId = :packetId")
    suspend fun deleteMulePacket(packetId: String)

    @Query("DELETE FROM mule_packets WHERE expiresAt <= :now")
    suspend fun pruneExpired(now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM mule_packets")
    suspend fun deleteAll()
}

