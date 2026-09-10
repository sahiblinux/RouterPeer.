package com.example.ui.mesh

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var selectedEphemeralMs by remember { mutableStateOf<Long?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Voice recording state
    val voiceRecorder = remember { VoiceNoteRecorder(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordDurationMs by remember { mutableLongStateOf(0L) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (voiceRecorder.startRecording()) {
                isRecordingVoice = true
                voiceRecordDurationMs = 0L
            }
        } else {
            Toast.makeText(context, "Microphone permission required for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                Toast.makeText(context, "Encrypting & preparing photo for mesh...", Toast.LENGTH_SHORT).show()
                val processed = MediaUtils.processPickedImage(context, uri)
                if (processed != null) {
                    viewModel.sendMediaMessage(
                        destination = destination,
                        mediaType = "IMAGE",
                        mediaBytes = processed.second,
                        localFilePath = processed.first.absolutePath,
                        text = inputText.trim(),
                        ephemeralDurationMs = selectedEphemeralMs
                    )
                    inputText = ""
                    showAttachmentMenu = false
                } else {
                    Toast.makeText(context, "Failed to load or compress image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                Toast.makeText(context, "Encrypting video for mesh payload...", Toast.LENGTH_SHORT).show()
                val processed = MediaUtils.processPickedVideo(context, uri)
                if (processed != null) {
                    viewModel.sendMediaMessage(
                        destination = destination,
                        mediaType = "VIDEO",
                        mediaBytes = processed.second,
                        localFilePath = processed.first.absolutePath,
                        text = inputText.trim(),
                        durationMs = processed.third,
                        ephemeralDurationMs = selectedEphemeralMs
                    )
                    inputText = ""
                    showAttachmentMenu = false
                } else {
                    Toast.makeText(context, "Failed to load video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Timer effect while voice recording
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            val startTime = System.currentTimeMillis()
            while (isActive && isRecordingVoice) {
                voiceRecordDurationMs = System.currentTimeMillis() - startTime
                delay(100)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecordingVoice) {
                voiceRecorder.cancelRecording()
            }
            AudioPlaybackManager.stop()
        }
    }

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

                    // Multimedia Attachment Selection Row
                    AnimatedVisibility(
                        visible = showAttachmentMenu,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberSurfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AttachmentOptionButton(
                                icon = Icons.Default.Image,
                                label = "Photo",
                                tint = NeonCyan,
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )

                            AttachmentOptionButton(
                                icon = Icons.Default.Videocam,
                                label = "Video",
                                tint = EmeraldShield,
                                onClick = {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                }
                            )

                            AttachmentOptionButton(
                                icon = Icons.Default.Mic,
                                label = "Voice Note",
                                tint = AmberPanic,
                                onClick = {
                                    showAttachmentMenu = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        if (voiceRecorder.startRecording()) {
                                            isRecordingVoice = true
                                            voiceRecordDurationMs = 0L
                                        }
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            )
                        }
                    }

                    // Input Row or Voice Recording Control Bar
                    if (isRecordingVoice) {
                        // Active voice recording state bar
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_recording")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "recording_dot"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    voiceRecorder.cancelRecording()
                                    isRecordingVoice = false
                                    voiceRecordDurationMs = 0L
                                    Toast.makeText(context, "Voice note discarded", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(CyberSurfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Cancel Recording",
                                    tint = AmberPanic,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(AmberPanic.copy(alpha = pulseAlpha))
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RECORDING ENCRYPTED AUDIO",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = AmberPanic
                                )
                                Text(
                                    text = formatDuration(voiceRecordDurationMs),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = TextPrimary
                                )
                            }

                            IconButton(
                                onClick = {
                                    val result = voiceRecorder.stopRecording()
                                    isRecordingVoice = false
                                    if (result != null) {
                                        viewModel.sendMediaMessage(
                                            destination = destination,
                                            mediaType = "VOICE_NOTE",
                                            mediaBytes = result.second,
                                            localFilePath = result.first.absolutePath,
                                            durationMs = result.third,
                                            text = inputText.trim(),
                                            ephemeralDurationMs = selectedEphemeralMs
                                        )
                                        inputText = ""
                                    } else {
                                        Toast.makeText(context, "Recording too short", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(NeonCyan)
                                    .testTag("send_voice_note_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Voice Note",
                                    tint = CyberBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        // Standard Input Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Attachment toggle button
                            IconButton(
                                onClick = { showAttachmentMenu = !showAttachmentMenu },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (showAttachmentMenu) NeonCyan else CyberSurfaceVariant)
                                    .testTag("attachment_menu_button")
                            ) {
                                Icon(
                                    imageVector = if (showAttachmentMenu) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = "Attach Media",
                                    tint = if (showAttachmentMenu) CyberBlack else NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

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

                            Spacer(modifier = Modifier.width(6.dp))

                            if (inputText.isBlank()) {
                                // Mic button for Voice Note recording
                                IconButton(
                                    onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            if (voiceRecorder.startRecording()) {
                                                isRecordingVoice = true
                                                voiceRecordDurationMs = 0L
                                            }
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(CyberSurfaceVariant)
                                        .border(1.dp, CyberBorder, CircleShape)
                                        .testTag("start_voice_record_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Record Voice Note",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                // Send Text Button
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
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(if (selectedEphemeralMs != null) AmberPanic else NeonCyan)
                                        .testTag("chat_send_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Encrypted Message",
                                        tint = CyberBlack,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Mule DTN",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DTN MULE ROUTING ACTIVE: Peer is out of radio range. Messages will be stored & automatically forwarded by intermediate mule nodes.",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )
                    }
                }
            }

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = CyberBorder,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "End-to-End Encrypted Channel",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )
                        Text(
                            text = "Send encrypted text, photos, videos, and voice notes.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(messages, key = { it.messageId }) { msg ->
                        MessageBubble(message = msg, isGroup = isGroup)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AttachmentOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(CyberSurface)
                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = TextPrimary
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, isGroup: Boolean) {
    val context = LocalContext.current
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
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
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

                if (isScrubbed) {
                    Text(
                        text = "[PURGED & ZEROED - ZERO TRACE]",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    )
                } else {
                    // Render according to mediaType
                    when (message.mediaType) {
                        "IMAGE" -> {
                            ImageMessageContent(message)
                        }
                        "VIDEO" -> {
                            VideoMessageContent(message, context)
                        }
                        "VOICE_NOTE" -> {
                            VoiceNoteMessageContent(message)
                        }
                        else -> {
                            if (message.content.isNotBlank()) {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                                )
                            }
                        }
                    }
                }

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

@Composable
private fun ImageMessageContent(message: MessageEntity) {
    val file = message.mediaUri?.let { File(it) }
    Column {
        if (file != null && file.exists()) {
            AsyncImage(
                model = file,
                contentDescription = "Encrypted Mesh Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
            )
        } else {
            Surface(
                color = CyberBlack,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Encrypted Image Attached", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }

        if (message.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message.content, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
        }

        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = CyberSurfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "PHOTO • AES-GCM",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoMessageContent(message: MessageEntity, context: Context) {
    val file = message.mediaUri?.let { File(it) }

    Column {
        Surface(
            color = CyberBlack,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (file != null && file.exists()) {
                        try {
                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Play Encrypted Video"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No video player available", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Video file not found", Toast.LENGTH_SHORT).show()
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(EmeraldShield.copy(alpha = 0.2f))
                        .border(1.dp, EmeraldShield, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = EmeraldShield,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Encrypted Video Clip",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = if (message.mediaDurationMs > 0) "Duration: ${formatDuration(message.mediaDurationMs)}" else "Tap to Play",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = EmeraldShield
                    )
                }
            }
        }

        if (message.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = message.content, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary))
        }
    }
}

@Composable
private fun VoiceNoteMessageContent(message: MessageEntity) {
    val currentUri by AudioPlaybackManager.currentlyPlayingUri.collectAsStateWithLifecycle()
    val isPlaying by AudioPlaybackManager.isPlaying.collectAsStateWithLifecycle()
    val progress by AudioPlaybackManager.progressFraction.collectAsStateWithLifecycle()
    val currentPosMs by AudioPlaybackManager.currentPositionMs.collectAsStateWithLifecycle()

    val isThisPlaying = (currentUri == message.mediaUri) && isPlaying
    val isThisActive = (currentUri == message.mediaUri)

    Surface(
        color = CyberBlack,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isThisPlaying) NeonCyan else CyberBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val path = message.mediaUri
                        if (path != null) {
                            AudioPlaybackManager.togglePlay(path)
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isThisPlaying) NeonCyan else CyberSurfaceVariant)
                ) {
                    Icon(
                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isThisPlaying) "Pause" else "Play",
                        tint = if (isThisPlaying) CyberBlack else NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VOICE NOTE (AAC)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NeonCyan
                            )
                        }

                        val displayDuration = if (isThisActive && currentPosMs > 0) {
                            "${formatDuration(currentPosMs)} / ${formatDuration(message.mediaDurationMs)}"
                        } else {
                            formatDuration(message.mediaDurationMs)
                        }
                        Text(
                            text = displayDuration,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { if (isThisActive) progress else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = NeonCyan,
                        trackColor = CyberSurfaceVariant
                    )
                }
            }

            if (message.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = message.content, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
            }
        }
    }
}
