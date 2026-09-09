package com.example.ui.gateway

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun GatewayScreen(
    state: GatewayUiState,
    onSetup: (String, String, String, String, (String) -> Unit) -> Unit,
    onUnlock: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is GatewayUiState.Loading -> {
                CircularProgressIndicator(
                    color = NeonCyan,
                    modifier = Modifier.testTag("gateway_loading_indicator")
                )
            }

            is GatewayUiState.SetupRequired -> {
                GatewaySetupContent(onSetup = onSetup)
            }

            is GatewayUiState.Locked -> {
                GatewayUnlockContent(
                    errorMessage = state.errorMessage,
                    failedAttempts = state.failedAttempts,
                    onUnlock = onUnlock
                )
            }

            is GatewayUiState.Unlocked -> {
                // Handled in parent host navigation
            }
        }
    }
}

@Composable
private fun GatewayUnlockContent(
    errorMessage: String?,
    failedAttempts: Int,
    onUnlock: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Futuristic Shield Icon Badge
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(CyberSurfaceVariant)
                .border(1.5.dp, NeonCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Secure Gateway",
                tint = NeonCyan,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ROUTERPEER",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = TextPrimary
        )

        Text(
            text = "DUAL-PASSWORD GATEWAY",
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = NeonCyan,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Zero-Knowledge Local Node Access",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp, bottom = 32.dp)
        )

        // Password Input
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Gateway Password", color = TextSecondary) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (password.isNotBlank()) onUnlock(password)
                }
            ),
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag("toggle_password_visibility")
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password visibility",
                        tint = TextSecondary
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = CyberBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = CyberSurface,
                unfocusedContainerColor = CyberSurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gateway_password_input")
        )

        AnimatedVisibility(visible = errorMessage != null) {
            Text(
                text = errorMessage.orEmpty(),
                color = AmberPanic,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("gateway_error_text")
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onUnlock(password) },
            enabled = password.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = CyberBlack,
                disabledContainerColor = CyberSurfaceVariant,
                disabledContentColor = TextMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("gateway_unlock_button")
        ) {
            Text(
                text = "AUTHENTICATE & DECRYPT",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Duress Policy Notice
        Surface(
            color = CyberSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Info",
                    tint = EmeraldShield,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Zero-Trace Duress Protection active. Inputting panic password triggers instant low-level purge.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun GatewaySetupContent(
    onSetup: (String, String, String, String, (String) -> Unit) -> Unit
) {
    var primaryPass by remember { mutableStateOf("") }
    var confirmPrimary by remember { mutableStateOf("") }
    var panicPass by remember { mutableStateOf("") }
    var confirmPanic by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Setup Security",
            tint = NeonCyan,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "INITIAL SECURITY GATEWAY",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = TextPrimary
        )

        Text(
            text = "Set your Primary Access Password and a covert Panic Wipe Password. No server or recovery email exists.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )

        // Section 1: Primary Password
        Text(
            text = "1. PRIMARY ACCESS PASSWORD",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = NeonCyan,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 6.dp)
        )

        OutlinedTextField(
            value = primaryPass,
            onValueChange = { primaryPass = it },
            label = { Text("Primary Password", color = TextSecondary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = CyberBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = CyberSurface,
                unfocusedContainerColor = CyberSurface
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_primary_password_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPrimary,
            onValueChange = { confirmPrimary = it },
            label = { Text("Confirm Primary Password", color = TextSecondary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = CyberBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = CyberSurface,
                unfocusedContainerColor = CyberSurface
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_confirm_primary_input")
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Panic Password
        Text(
            text = "2. COVERT PANIC PASSWORD (DURESS)",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = AmberPanic,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 6.dp)
        )

        OutlinedTextField(
            value = panicPass,
            onValueChange = { panicPass = it },
            label = { Text("Panic Wipe Password", color = TextSecondary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberPanic,
                unfocusedBorderColor = CyberBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = CyberSurface,
                unfocusedContainerColor = CyberSurface
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_panic_password_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = confirmPanic,
            onValueChange = { confirmPanic = it },
            label = { Text("Confirm Panic Password", color = TextSecondary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberPanic,
                unfocusedBorderColor = CyberBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = CyberSurface,
                unfocusedContainerColor = CyberSurface
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup_confirm_panic_input")
        )

        AnimatedVisibility(visible = validationError != null) {
            Text(
                text = validationError.orEmpty(),
                color = AmberPanic,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Warning Box
        Surface(
            color = CyberSurface,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AmberPanic.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Panic Warning",
                    tint = AmberPanic,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "WARNING: Entering the Panic Password on the lock screen silently wipes all cryptographic keys, Room database, message histories, and terminates the OS process instantly.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                validationError = null
                onSetup(primaryPass, confirmPrimary, panicPass, confirmPanic) { error ->
                    validationError = error
                }
            },
            enabled = primaryPass.isNotBlank() && panicPass.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = CyberBlack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("setup_initialize_button")
        ) {
            Text(
                text = "INITIALIZE SECURE NODE",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}
