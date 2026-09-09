package com.example.ui.mesh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.LocalKeyring
import com.example.data.db.PeerEntity
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

@Composable
fun MyIdentityQrDialog(
    keyring: LocalKeyring,
    qrBitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rawJson = remember(keyring) { keyring.toIdentityPayload().toJson() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Node Identity QR",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Peer devices scan this high-contrast QR code or import raw payload to pair without servers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // High Contrast QR Code Canvas
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(2.dp, NeonCyan, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Node Identity QR Code",
                            modifier = Modifier.size(204.dp)
                        )
                    } else {
                        Text("Generating QR...", color = Color.Black, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Node ID badge
                Surface(
                    color = CyberSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NODE ID: ${keyring.nodeId}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            ),
                            color = NeonCyan
                        )
                        Text(
                            text = "Alias: ${keyring.alias}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("RouterPeer Node Identity", rawJson))
                        Toast.makeText(context, "Node Identity JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("copy_identity_json_button")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Raw Identity JSON", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack)
            ) {
                Text("CLOSE", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun PairPeerDialog(
    onDismiss: () -> Unit,
    onPair: (String) -> Unit
) {
    var rawInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Pair Peer Contact",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Paste the raw JSON payload scanned or shared from another RouterPeer node:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    placeholder = { Text("{\"nodeId\":\"XXXX-XXXX\", ...}", color = TextMuted) },
                    minLines = 4,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = CyberBlack,
                        unfocusedContainerColor = CyberBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("peer_json_input")
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = { onPair(rawInput) },
                enabled = rawInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack),
                modifier = Modifier.testTag("submit_pair_peer_button")
            ) {
                Text("PAIR & DERIVE KEY", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun CreateGroupDialog(
    availablePeers: List<PeerEntity>,
    onDismiss: () -> Unit,
    onCreate: (groupName: String, selectedMemberIds: List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedPeers = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Create Encrypted Mesh Group",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "A 256-bit symmetric group key will be generated and individually encrypted for each selected member.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = CyberBlack,
                        unfocusedContainerColor = CyberBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Select Group Members:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (availablePeers.isEmpty()) {
                    Text(
                        text = "No peers paired yet. You can still create the group and invite peers later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(availablePeers) { peer ->
                            val isSelected = selectedPeers.contains(peer.nodeId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selectedPeers.remove(peer.nodeId)
                                        else selectedPeers.add(peer.nodeId)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedPeers.add(peer.nodeId)
                                        else selectedPeers.remove(peer.nodeId)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonCyan,
                                        checkmarkColor = CyberBlack,
                                        uncheckedColor = CyberBorder
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(peer.alias, color = TextPrimary, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                    Text(peer.nodeId, color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(groupName, selectedPeers.toList()) },
                enabled = groupName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBlack),
                modifier = Modifier.testTag("submit_create_group_button")
            ) {
                Text("CREATE & BROADCAST KEY", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun EmergencyWipeConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmWipe: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AmberPanic,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TRIGGER PANIC WIPE",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberPanic,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        },
        text = {
            Text(
                text = "Are you sure you want to execute an absolute factory purge? This action immediately deletes the Room database, overwrites cryptographic keyrings, purges app cache, and terminates the OS process.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ABORT", color = TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmWipe,
                colors = ButtonDefaults.buttonColors(containerColor = AmberPanic, contentColor = Color.White),
                modifier = Modifier.testTag("confirm_panic_wipe_button")
            ) {
                Text("PURGE EVERYTHING", fontWeight = FontWeight.Bold)
            }
        }
    )
}
