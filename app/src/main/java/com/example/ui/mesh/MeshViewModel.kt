package com.example.ui.mesh

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoManager
import com.example.crypto.IdentityPayload
import com.example.crypto.LocalKeyring
import com.example.data.db.GroupEntity
import com.example.data.db.MessageEntity
import com.example.data.db.PeerEntity
import com.example.data.repository.MeshRepository
import com.example.mesh.MeshStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ActiveChatDestination {
    data class Direct(val peer: PeerEntity) : ActiveChatDestination
    data class Group(val group: GroupEntity) : ActiveChatDestination
}

class MeshViewModel(
    private val repository: MeshRepository,
    val isDecoyMode: Boolean = false
) : ViewModel() {

    val keyring: LocalKeyring = repository.keyring

    val peers: StateFlow<List<PeerEntity>> = repository.allPeers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<GroupEntity>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMessages: StateFlow<List<MessageEntity>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val meshStats: StateFlow<MeshStats> = repository.meshStats
    val isAdvertising: StateFlow<Boolean> = repository.isAdvertising
    val isDiscovering: StateFlow<Boolean> = repository.isDiscovering
    val connectedEndpoints: StateFlow<Map<String, String>> = repository.connectedEndpoints

    val mulePacketCount: StateFlow<Int> = repository.mulePacketCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isAcousticTransmitting: StateFlow<Boolean> = repository.acousticModem.isTransmitting
    val isAcousticListening: StateFlow<Boolean> = repository.acousticModem.isListening
    val lastAcousticReceived: StateFlow<String?> = repository.acousticModem.lastReceivedData

    private val _activeChat = MutableStateFlow<ActiveChatDestination?>(null)
    val activeChat: StateFlow<ActiveChatDestination?> = _activeChat.asStateFlow()

    private val _qrCodeBitmap = MutableStateFlow<Bitmap?>(null)
    val qrCodeBitmap: StateFlow<Bitmap?> = _qrCodeBitmap.asStateFlow()

    init {
        generateIdentityQrCode()
        if (!isDecoyMode) {
            repository.startMesh()
        }
    }

    private fun generateIdentityQrCode() {
        viewModelScope.launch(Dispatchers.IO) {
            val identityJson = keyring.toIdentityPayload().toJson()
            val bmp = CryptoManager.generateHighContrastQrCode(identityJson, size = 512)
            _qrCodeBitmap.value = bmp
        }
    }

    fun openDirectChat(peer: PeerEntity) {
        _activeChat.value = ActiveChatDestination.Direct(peer)
    }

    fun openGroupChat(group: GroupEntity) {
        _activeChat.value = ActiveChatDestination.Group(group)
    }

    fun closeChat() {
        _activeChat.value = null
    }

    fun sendDirectMessage(recipientNodeId: String, text: String, ephemeralDurationMs: Long? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendDirectMessage(recipientNodeId, text, ephemeralDurationMs)
        }
    }

    fun sendEmergencySosBeacon(distressType: String, notes: String) {
        viewModelScope.launch {
            repository.sendEmergencySosBeacon(distressType, notes)
        }
    }

    fun transmitAcoustic(payload: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.acousticModem.transmitAcousticData(payload) {
                onComplete()
            }
        }
    }

    fun startAcousticListening(onDecoded: (String) -> Unit) {
        repository.acousticModem.startListening(onDecoded)
    }

    fun stopAcousticListening() {
        repository.acousticModem.stopListening()
    }

    fun sendGroupMessage(groupId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendGroupMessage(groupId, text)
        }
    }

    fun createGroup(groupName: String, selectedMemberIds: List<String>, onCreated: (String) -> Unit) {
        if (groupName.isBlank()) return
        viewModelScope.launch {
            val groupId = repository.createGroup(groupName, selectedMemberIds)
            onCreated(groupId)
        }
    }

    fun importPeerFromPayload(rawJsonOrPayload: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val payload = IdentityPayload.fromJson(rawJsonOrPayload.trim())
            if (payload == null) {
                onResult(false, "Invalid QR code payload format")
                return@launch
            }
            if (payload.nodeId == keyring.nodeId) {
                onResult(false, "Cannot pair your own Node ID")
                return@launch
            }
            val success = repository.pairPeer(payload)
            if (success) {
                onResult(true, "Successfully paired peer: ${payload.alias} (${payload.nodeId})")
            } else {
                onResult(false, "Failed to pair peer")
            }
        }
    }

    fun toggleAdvertising(enable: Boolean) {
        repository.toggleAdvertising(enable)
    }

    fun toggleDiscovery(enable: Boolean) {
        repository.toggleDiscovery(enable)
    }

    fun restartMesh() {
        repository.stopMesh()
        repository.startMesh()
    }

    fun getDirectMessagesFlow(peerNodeId: String) = repository.getDirectConversation(peerNodeId)
    fun getGroupMessagesFlow(groupId: String) = repository.getGroupConversation(groupId)

    override fun onCleared() {
        super.onCleared()
        repository.stopMesh()
    }

    class Factory(
        private val repository: MeshRepository,
        private val isDecoyMode: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MeshViewModel(repository, isDecoyMode) as T
        }
    }

}
