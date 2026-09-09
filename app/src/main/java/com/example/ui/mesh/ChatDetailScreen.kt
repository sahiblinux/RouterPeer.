package com.example.ui.mesh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.MessageEntity
import com.example.ui.theme.AmberPanic
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    destination: ActiveChatDestination,
    viewModel: MeshViewModel,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedEphemeralMs by remember { mutableStateOf<Long?>(null) }
    var showEphemeralMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val title: String
    val subtitle: String
    val isGroup = destination is ActiveChatDestination.Group
    val isDirectDisconnected = (destination is ActiveChatDestination.Direct) && !destination.peer.isNearbyConnected

    val messagesFlow = remember(destination) {
        when (destination) {
            is ActiveChatDestination.Direct -> viewModel.getDirectMessagesFlow(destination.peer.nodeId)
            is ActiveChatDestination.Group -> viewModel.getGroupMessagesFlow(destination.group.groupId)
        }
    }
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    when (destination) {
        is ActiveChatDestination.Direct -> {
            title = destination.peer.alias
            subtitle = "Node ${destination.peer.nodeId} • E2EE (X25519)"
        }
        is ActiveChatDestination.Group -> {
            title = destination.group.groupName
            subtitle = "Group ${destination.group.groupId} • Mesh Flooding"
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = CyberBlack,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "E2EE Secured",
                                tint = EmeraldShield,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = NeonCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    // Encryption Specs Badge
                    Surface(
                        color = CyberSurfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (isGroup) "GROUP AES-GCM" else "AES-256-GCM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberSurface
                )
            )
        },
        bottomBar = {
            Surface(
                color = CyberSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.imePadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Ephemeral timer bar for Direct chats
                    if (destination is ActiveChatDestination.Direct) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Disappearing timer",
                                tint = if (selectedEphemeralMs != null) AmberPanic else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Self-Destruct:",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            val options = listOf(
                                "OFF" to null,
                                "30s" to 30_000L,
                                "5m" to 300_000L,
                                "1h" to 3600_000L,
                                "24h" to 86400_000L
                            )
                            options.forEach { (label, duration) ->
                                val isSelected = selectedEphemeralMs == duration
                                Surface(
                                    color = if (isSelected) (if (duration != null) AmberPanic else NeonCyan) else CyberSurfaceVariant,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .clickable { selectedEphemeralMs = duration }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = if (isSelected) CyberBlack else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    if (selectedEphemeralMs != null) "Self-destructing message..." else "Encrypted message...",
                                    color = TextMuted
                                )
                            },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (selectedEphemeralMs != null) AmberPanic else NeonCyan,
                                unfocusedBorderColor = CyberBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = CyberBlack,
                                unfocusedContainerColor = CyberBlack
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotEmpty()) {
                                    when (destination) {
                                        is ActiveChatDestination.Direct -> viewModel.sendDirectMessage(
                                            destination.peer.nodeId,
                                            text,
                                            ephemeralDurationMs = selectedEphemeralMs
                                        )
                                        is ActiveChatDestination.Group -> viewModel.sendGroupMessage(
                                            destination.group.groupId,
                                            text
                                        )
                                    }
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (inputText.isNotBlank()) (if (selectedEphemeralMs != null) AmberPanic else NeonCyan) else CyberSurfaceVariant)
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Encrypted Message",
                                tint = if (inputText.isNotBlank()) CyberBlack else TextMuted
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CyberBlack)
        ) {
            if (isDirectDisconnected) {
                Surface(
                    color = CyberSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DTN Mule Active: Recipient offline. Message buffered in encrypted relay queue until proximity contact.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }
            }
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Encrypted Channel Ready",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Messages are signed with Ed25519 and encrypted using AES-256-GCM. Transmitted purely peer-to-peer via Google Nearby Connections mesh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.messageId }) { msg ->
                        MessageBubble(message = msg, isGroup = isGroup)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, isGroup: Boolean) {
    val isOutgoing = message.isOutgoing
    val isSos = message.status == "EMERGENCY_SOS" || message.content.startsWith("[EMERGENCY SOS BEACON]")
    val isScrubbed = message.isScrubbed || message.content.contains("[PURGED")

    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = when {
        isSos -> AmberPanic.copy(alpha = 0.2f)
        isScrubbed -> CyberSurfaceVariant.copy(alpha = 0.5f)
        isOutgoing -> CyberSurfaceVariant
        else -> CyberSurface
    }
    val borderColor = when {
        isSos -> AmberPanic
        isScrubbed -> CyberBorder.copy(alpha = 0.4f)
        isOutgoing -> NeonCyan.copy(alpha = 0.5f)
        else -> CyberBorder
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (isGroup && !isOutgoing) {
            Text(
                text = "Node ${message.senderNodeId}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (isSos) AmberPanic else NeonCyan,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            )
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isOutgoing) 14.dp else 2.dp,
                bottomEnd = if (isOutgoing) 2.dp else 14.dp
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (isSos) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS",
                            tint = AmberPanic,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "EMERGENCY SOS FLOOD",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = AmberPanic
                        )
                    }
                }

                Text(
                    text = if (isScrubbed) "[PURGED & ZEROED - ZERO TRACE]" else message.content,
                    style = if (isScrubbed) {
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (message.ephemeralDurationMs != null && !isScrubbed) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Ephemeral",
                            tint = AmberPanic,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = when (message.ephemeralDurationMs) {
                                30_000L -> "30s"
                                300_000L -> "5m"
                                3600_000L -> "1h"
                                86400_000L -> "24h"
                                else -> "${message.ephemeralDurationMs / 1000}s"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = AmberPanic,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    if (message.isVerified) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Ed25519 Verified Signature",
                            tint = EmeraldShield,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }

                    if (message.hops > 0) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Hops",
                            tint = NeonCyan,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${message.hops}h",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = NeonCyan,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    Text(
                        text = timeFormat.format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextMuted
                    )
                }
            }
        }
    }
}
