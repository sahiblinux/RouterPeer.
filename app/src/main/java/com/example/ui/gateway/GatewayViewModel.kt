package com.example.ui.gateway

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crypto.LocalKeyring
import com.example.data.SecurePreferences
import com.example.data.SecureWipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI States for the Dual-Password Gateway.
 */
sealed interface GatewayUiState {
    data object Loading : GatewayUiState
    data object SetupRequired : GatewayUiState
    data class Locked(
        val failedAttempts: Int = 0,
        val errorMessage: String? = null
    ) : GatewayUiState
    data class Unlocked(val keyring: LocalKeyring, val isDecoy: Boolean = false) : GatewayUiState
}

/**
 * Dual-Password Gateway ViewModel.
 * Governs the locked entry splash screen and mediates:
 * 1. Primary Password Verification -> Normal app unlock & database decryption.
 * 2. Panic Password Verification -> Silent, instant, low-level factory reset & process kill.
 */
class GatewayViewModel(
    private val appContext: Context,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<GatewayUiState>(GatewayUiState.Loading)
    val uiState: StateFlow<GatewayUiState> = _uiState.asStateFlow()

    private var failedAttemptsCount = 0

    init {
        checkGatewayConfiguration()
    }

    fun checkGatewayConfiguration() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!securePreferences.isGatewayConfigured()) {
                    _uiState.value = GatewayUiState.SetupRequired
                } else {
                    _uiState.value = GatewayUiState.Locked(failedAttempts = failedAttemptsCount)
                }
            }
        }
    }

    /**
     * Completes initial setup by registering both the Primary Access Password
     * and the covert Panic Password.
     */
    fun setupCredentials(
        primaryPass: String,
        confirmPrimaryPass: String,
        panicPass: String,
        confirmPanicPass: String,
        decoyPass: String? = null,
        confirmDecoyPass: String? = null,
        onValidationError: (String) -> Unit
    ) {
        if (primaryPass.length < 4) {
            onValidationError("Primary password must be at least 4 characters")
            return
        }
        if (primaryPass != confirmPrimaryPass) {
            onValidationError("Primary passwords do not match")
            return
        }
        if (panicPass.length < 4) {
            onValidationError("Panic password must be at least 4 characters")
            return
        }
        if (panicPass != confirmPanicPass) {
            onValidationError("Panic passwords do not match")
            return
        }
        if (primaryPass == panicPass) {
            onValidationError("Panic password MUST differ from primary password")
            return
        }
        if (!decoyPass.isNullOrBlank()) {
            if (decoyPass.length < 4) {
                onValidationError("Decoy password must be at least 4 characters")
                return
            }
            if (decoyPass != confirmDecoyPass) {
                onValidationError("Decoy passwords do not match")
                return
            }
            if (decoyPass == primaryPass || decoyPass == panicPass) {
                onValidationError("Decoy password must differ from Primary and Panic passwords")
                return
            }
        }

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Configure salted & hashed passwords in EncryptedSharedPreferences
                securePreferences.configurePasswords(primaryPass, panicPass, decoyPass)
                // Initialize user's zero-data cryptographic keyring & 8-digit Node ID
                val keyring = securePreferences.getOrCreateKeyring()
                _uiState.value = GatewayUiState.Unlocked(keyring)
            }
        }
    }

    /**
     * Verifies the entered password against the dual-password gateway.
     * Triggers normal access for Primary Password, decoy access for Decoy Password,
     * or an instantaneous silent wipe for Panic Password.
     */
    fun submitPassword(enteredPass: String) {
        if (enteredPass.isBlank()) return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                securePreferences.verifyPassword(enteredPass)
            }

            when (result) {
                SecurePreferences.PasswordVerificationResult.PRIMARY_SUCCESS -> {
                    // Normal access granted
                    failedAttemptsCount = 0
                    val keyring = withContext(Dispatchers.IO) {
                        securePreferences.getOrCreateKeyring()
                    }
                    _uiState.value = GatewayUiState.Unlocked(keyring, isDecoy = false)
                }

                SecurePreferences.PasswordVerificationResult.DECOY_SUCCESS -> {
                    // Decoy / Guest Vault access granted
                    failedAttemptsCount = 0
                    val decoyKeyring = withContext(Dispatchers.IO) {
                        securePreferences.getDecoyKeyring()
                    }
                    _uiState.value = GatewayUiState.Unlocked(decoyKeyring, isDecoy = true)
                }

                SecurePreferences.PasswordVerificationResult.PANIC_TRIGGERED -> {
                    // DURESS / PANIC TRIGGERED:
                    // Instantly and silently execute zero-trace low-level factory reset
                    withContext(Dispatchers.IO) {
                        SecureWipe.executePanicWipe(appContext)
                    }
                }

                SecurePreferences.PasswordVerificationResult.INVALID -> {
                    failedAttemptsCount++
                    _uiState.value = GatewayUiState.Locked(
                        failedAttempts = failedAttemptsCount,
                        errorMessage = "Access Denied: Authentication Failed"
                    )
                }
            }
        }
    }

    /**
     * Direct emergency panic wipe invocation (e.g. from Emergency Settings button).
     */
    fun triggerEmergencyWipe() {
        viewModelScope.launch(Dispatchers.IO) {
            SecureWipe.executePanicWipe(appContext)
        }
    }

    fun lockGateway() {
        _uiState.value = GatewayUiState.Locked(failedAttempts = 0)
    }

    class Factory(
        private val context: Context,
        private val securePreferences: SecurePreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GatewayViewModel(context.applicationContext, securePreferences) as T
        }
    }
}
