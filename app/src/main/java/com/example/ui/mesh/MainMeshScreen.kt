package com.example.ui.mesh

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.crypto.toHex
import com.example.data.db.GroupEntity
import com.example.data.db.PeerEntity
import com.example.ui.theme.AmberPanic
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMeshScreen(
    viewModel: MeshViewModel,
    onLockGateway: () -> Unit,
    onEmergencyWipe: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog states
    var showQrDialog by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showSosDialog by remember { mutableStateOf(false) }
    var showAcousticDialog by remember { mutableStateOf(false) }

    val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val stats by viewModel.meshStats.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrCodeBitmap.collectAsStateWithLifecycle()

    // If a chat is active, display the full-screen chat screen
    if (activeChat != null) {
        ChatDetailScreen(
            destination = activeChat!!,
            viewModel = viewModel,
            onBack = { viewModel.closeChat() }
        )
        return
    }

    Scaffold(
        containerColor = CyberBlack,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "RouterPeer",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            ),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Clickable Node ID badge
                        Surface(
                            color = CyberSurfaceVariant,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable { showQrDialog = true }
                        ) {
                            Text(
                                text = "[${viewModel.keyring.nodeId}]",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = NeonCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (viewModel.isDecoyMode) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = AmberWarning.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning)
                            ) {
                                Text(
                                    text = "DECOY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.sp
                                    ),
                                    color = AmberWarning,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Emergency SOS Beacon Button
                    IconButton(
                        onClick = { showSosDialog = true },
                        modifier = Modifier.testTag("sos_beacon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Mesh SOS Beacon",
                            tint = AmberPanic
                        )
                    }

                    // Acoustic Modem Fallback Button
                    IconButton(
                        onClick = { showAcousticDialog = true },
                        modifier = Modifier.testTag("acoustic_modem_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Acoustic Pairing & Fallback",
                            tint = NeonCyan
                        )
                    }

                    // Active Links Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (stats.activeLinks > 0) EmeraldShield else AmberWarning)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stats.activeLinks} Mesh",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TextPrimary
                        )
                    }

                    // Lock button
                    IconButton(
                        onClick = onLockGateway,
                        modifier = Modifier.testTag("lock_gateway_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Gateway",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CyberSurface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.AutoMirrored.Filled.Chat else Icons.Outlined.Chat,
                            contentDescription = "Chats"
                        )
                    },
                    label = { Text("Comms") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = CyberSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tab_chats")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Default.Hub else Icons.Outlined.Hub,
                            contentDescription = "Mesh Topology"
                        )
                    },
                    label = { Text("Topology") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = CyberSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tab_topology")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Default.Security else Icons.Outlined.Security,
                            contentDescription = "Identity Vault"
                        )
                    },
                    label = { Text("Vault") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = CyberSurfaceVariant
                    ),
                    modifier = Modifier.testTag("tab_vault")
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showCreateGroupDialog = true },
                    containerColor = NeonCyan,
                    contentColor = CyberBlack,
                    modifier = Modifier.testTag("fab_create_group")
                ) {
                    Icon(imageVector = Icons.Default.Groups, contentDescription = "Create Group")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CyberBlack)
        ) {
            when (selectedTab) {
                0 -> CommsTab(
                    peers = peers,
                    groups = groups,
                    onPeerClick = { viewModel.openDirectChat(it) },
                    onGroupClick = { viewModel.openGroupChat(it) },
                    onOpenPairDialog = { showPairDialog = true }
                )
                1 -> TopologyTab(
                    viewModel = viewModel,
                    stats = stats,
                    peers = peers
                )
                2 -> VaultTab(
                    viewModel = viewModel,
                    onShowQr = { showQrDialog = true },
                    onOpenPair = { showPairDialog = true },
                    onOpenAcoustic = { showAcousticDialog = true },
                    onTriggerPanic = { showWipeConfirmDialog = true }
                )
            }
        }
    }

    // Dialogs
    if (showQrDialog) {
        MyIdentityQrDialog(
            keyring = viewModel.keyring,
            qrBitmap = qrBitmap,
            onDismiss = { showQrDialog = false }
        )
    }

    if (showPairDialog) {
        PairPeerDialog(
            onDismiss = { showPairDialog = false },
            onPair = { rawJson ->
                viewModel.importPeerFromPayload(rawJson) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    if (success) showPairDialog = false
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            availablePeers = peers,
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, memberIds ->
                viewModel.createGroup(name, memberIds) {
                    showCreateGroupDialog = false
                    Toast.makeText(context, "Group created & keys distributed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showWipeConfirmDialog) {
        EmergencyWipeConfirmationDialog(
            onDismiss = { showWipeConfirmDialog = false },
            onConfirmWipe = {
                showWipeConfirmDialog = false
                onEmergencyWipe()
            }
        )
    }

    if (showSosDialog) {
        EmergencySosBeaconDialog(
            onDismiss = { showSosDialog = false },
            onBroadcast = { distressType, notes ->
                viewModel.sendEmergencySosBeacon(distressType, notes)
                Toast.makeText(context, "Emergency SOS beacon flooded across mesh nodes!", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showAcousticDialog) {
        AcousticModemDialog(
            viewModel = viewModel,
            onDismiss = { showAcousticDialog = false }
        )
    }
}

@Composable
private fun CommsTab(
    peers: List<PeerEntity>,
    groups: List<GroupEntity>,
    onPeerClick: (PeerEntity) -> Unit,
    onGroupClick: (GroupEntity) -> Unit,
    onOpenPairDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Pair Peer Header Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Add Peer Contact", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text("Import peer QR payload to exchange ECDH keys", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Button(
                        onClick = onOpenPairDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant, contentColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Pair", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: Mesh Groups
        item {
            Text(
                text = "MESH GROUPS (${groups.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = NeonCyan
            )
        }

        if (groups.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Mesh Groups Created", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Tap '+' below to create an encrypted group broadcast", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextMuted)
                    }
                }
            }
        } else {
            items(groups, key = { it.groupId }) { group ->
                GroupItemCard(group = group, onClick = { onGroupClick(group) })
            }
        }

        // Section: Direct Peer Channels
        item {
            Text(
                text = "DIRECT PEER CHANNELS (${peers.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = EmeraldShield
            )
        }

        if (peers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Peer Contacts Yet", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Pair via QR code or Nearby mesh auto-discovery", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextMuted)
                    }
                }
            }
        } else {
            items(peers, key = { it.nodeId }) { peer ->
                PeerItemCard(peer = peer, onClick = { onPeerClick(peer) })
            }
        }
    }
}

@Composable
private fun GroupItemCard(group: GroupEntity, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CyberSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Groups, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.groupName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                Text(
                    text = "ID: ${group.groupId} • Broadcast Flood",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            }
            Surface(
                color = CyberSurfaceVariant,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
            ) {
                Text(
                    text = "AES-GCM",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = NeonCyan,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PeerItemCard(peer: PeerEntity, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CyberSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.NearMe, contentDescription = null, tint = EmeraldShield, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(peer.alias, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    if (peer.isNearbyConnected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(EmeraldShield)
                        )
                    }
                }
                Text(
                    text = "Node ${peer.nodeId} ${if (peer.isNearbyConnected) "• P2P Connected" else "• Paired"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (peer.isNearbyConnected) EmeraldShield else TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Secured",
                tint = NeonCyan,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TopologyTab(
    viewModel: MeshViewModel,
    stats: com.example.mesh.MeshStats,
    peers: List<PeerEntity>
) {
    val isAdv by viewModel.isAdvertising.collectAsStateWithLifecycle()
    val isDisc by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val muleCount by viewModel.mulePacketCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Controls & Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MESH RADIO PROTOCOL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("P2P Mesh Advertising", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Text("Broadcast node beacon over Wi-Fi / Bluetooth", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = isAdv,
                            onCheckedChange = { viewModel.toggleAdvertising(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = CyberSurfaceVariant)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Continuous Mesh Discovery", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Text("Auto-scan for neighboring RouterPeer nodes", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = isDisc,
                            onCheckedChange = { viewModel.toggleDiscovery(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = CyberSurfaceVariant)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.restartMesh()
                            Toast.makeText(context, "Restarting mesh transceivers...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant, contentColor = TextPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-synchronize Mesh Transceivers", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Live Mesh Telemetry Counters
        item {
            Text(
                text = "NETWORK TELEMETRY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = EmeraldShield
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(title = "ACTIVE LINKS", value = "${stats.activeLinks}", modifier = Modifier.weight(1f), accent = EmeraldShield)
                StatTile(title = "ROUTED HOPS", value = "${stats.packetsRouted}", modifier = Modifier.weight(1f), accent = NeonCyan)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(title = "PACKETS SENT", value = "${stats.packetsSent}", modifier = Modifier.weight(1f), accent = TextPrimary)
                StatTile(title = "PACKETS RECV", value = "${stats.packetsReceived}", modifier = Modifier.weight(1f), accent = TextPrimary)
            }
        }

        // Store-and-Forward (DTN Mule) status
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "DTN MULE ROUTING BUFFER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            ),
                            color = NeonCyan
                        )
                        Text(
                            text = "$muleCount buffered",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (muleCount > 0) AmberWarning else EmeraldShield
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Store-and-forward delay-tolerant routing. Encrypted packets intended for offline/unreachable peers are securely held in intermediate nodes and offloaded when recipient radio comes in proximity range.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Discovered Mesh Nodes list
        item {
            Text(
                text = "DISCOVERED MESH TOPOLOGY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = NeonCyan
            )
        }

        if (peers.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Scanning decentralized local frequencies via Google Nearby Connections (P2P_CLUSTER)...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(peers) { peer ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.SignalCellularAlt, contentDescription = null, tint = if (peer.isNearbyConnected) EmeraldShield else TextMuted)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peer.alias, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Text("Node: ${peer.nodeId}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                            if (peer.nearbyEndpointId != null) {
                                Text("Endpoint: ${peer.nearbyEndpointId}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace), color = NeonCyan)
                            }
                        }
                        Surface(
                            color = if (peer.isNearbyConnected) EmeraldShield.copy(alpha = 0.15f) else CyberSurfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (peer.isNearbyConnected) "DIRECT P2P" else "KNOWN NODE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (peer.isNearbyConnected) EmeraldShield else TextMuted
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier = Modifier, accent: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace), color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace), color = accent)
        }
    }
}

@Composable
private fun VaultTab(
    viewModel: MeshViewModel,
    onShowQr: () -> Unit,
    onOpenPair: () -> Unit,
    onOpenAcoustic: () -> Unit,
    onTriggerPanic: () -> Unit
) {
    val keyring = viewModel.keyring

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (viewModel.isDecoyMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GUEST VAULT / DECOY ACTIVE",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = AmberWarning,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Authenticated with Decoy credential. Primary cryptographic identity, authentic direct conversations, and sensitive keys are segregated and completely hidden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
        // Zero-Data Identity Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ZERO-DATA ARCHITECTURE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = NeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "This device is an autonomous mesh router node. No telemetry, email, or central accounts are used.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    KeySpecRow(label = "NODE ID (SecureRandom)", value = keyring.nodeId)
                    KeySpecRow(label = "ALIAS", value = keyring.alias)
                    KeySpecRow(label = "X25519 PUBKEY (ECDH)", value = keyring.x25519PublicKey.toHex().take(16) + "..." + keyring.x25519PublicKey.toHex().takeLast(8))
                    KeySpecRow(label = "ED25519 PUBKEY (SIGN)", value = keyring.ed25519PublicKey.toHex().take(16) + "..." + keyring.ed25519PublicKey.toHex().takeLast(8))

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onShowQr,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("My QR Code", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }

                        OutlinedButton(
                            onClick = onOpenPair,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pair Peer", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onOpenAcoustic,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceVariant, contentColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Acoustic Modem (Air-Gapped Audio)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        // Duress Panic Wipe Protocol Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberPanic.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AmberPanic, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PANIC-WIPE PROTOCOL",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = AmberPanic
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Entering your Panic Password on the entry lock screen immediately executes zero-trace eradication:\n• Overwrites & deletes Room database\n• Purges EncryptedSharedPreferences\n• Clears application cache & files\n• Kills the OS process instantly via killProcess()",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onTriggerPanic,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPanic, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("panic_wipe_manual_button")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EMERGENCY FACTORY PURGE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeySpecRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace), color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = TextPrimary)
    }
}
