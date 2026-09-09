package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SecurePreferences
import com.example.data.db.AppDatabase
import com.example.data.repository.MeshRepository
import com.example.mesh.NearbyMeshManager
import com.example.ui.gateway.GatewayScreen
import com.example.ui.gateway.GatewayUiState
import com.example.ui.gateway.GatewayViewModel
import com.example.ui.mesh.MainMeshScreen
import com.example.ui.mesh.MeshViewModel
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val gatewayViewModel: GatewayViewModel by viewModels {
        val app = application as RouterPeerApp
        GatewayViewModel.Factory(this, app.securePreferences)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                PermissionRequestWrapper {
                    RouterPeerAppHost(gatewayViewModel = gatewayViewModel)
                }
            }
        }
    }
}

@Composable
fun PermissionRequestWrapper(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val requiredPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled; Nearby will operate with available adapters
    }

    LaunchedEffect(Unit) {
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    content()
}

@Composable
fun RouterPeerAppHost(gatewayViewModel: GatewayViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gatewayState by gatewayViewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CyberBlack
    ) {
        when (val state = gatewayState) {
            is GatewayUiState.Unlocked -> {
                // Keyring is decrypted and available. Construct mesh dependencies.
                val app = context.applicationContext as RouterPeerApp
                val database = remember(state.keyring.nodeId) {
                    AppDatabase.getInstance(context)
                }
                val meshManager = remember(state.keyring.nodeId) {
                    NearbyMeshManager(context, database, state.keyring)
                }
                val repository = remember(state.keyring.nodeId) {
                    MeshRepository(database, app.securePreferences, meshManager, state.keyring)
                }
                val meshViewModel: MeshViewModel = viewModel(
                    key = state.keyring.nodeId,
                    factory = MeshViewModel.Factory(repository)
                )

                MainMeshScreen(
                    viewModel = meshViewModel,
                    onLockGateway = {
                        gatewayViewModel.lockGateway()
                    },
                    onEmergencyWipe = {
                        gatewayViewModel.triggerEmergencyWipe()
                    }
                )
            }

            else -> {
                GatewayScreen(
                    state = state,
                    onSetup = { primary, confirmPrimary, panic, confirmPanic, onError ->
                        gatewayViewModel.setupCredentials(
                            primaryPass = primary,
                            confirmPrimaryPass = confirmPrimary,
                            panicPass = panic,
                            confirmPanicPass = confirmPanic,
                            onValidationError = onError
                        )
                    },
                    onUnlock = { password ->
                        gatewayViewModel.submitPassword(password)
                    }
                )
            }
        }
    }
}
