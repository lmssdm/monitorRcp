package com.tcc.monitorrcp.ui

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.tcc.monitorrcp.presentation.theme.MonitorRcpTheme
// [ALTERAÇÃO] Imports das novas telas
import com.tcc.monitorrcp.ui.screens.SplashScreen
import com.tcc.monitorrcp.ui.screens.TimerScreen
// ---
import com.tcc.monitorrcp.ui.viewmodel.WearViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.values.all { it }) {
            Toast.makeText(this, "Permissões são necessárias.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicita permissões básicas
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )

        setContent {
            MonitorRcpTheme {
                val uiState by viewModel.uiState.collectAsState()
                var showSplashScreen by remember { mutableStateOf(true) }

                // Mantém a tela acesa enquanto está capturando
                LaunchedEffect(uiState.isCapturing) {
                    if (uiState.isCapturing) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Tela de splash
                LaunchedEffect(Unit) {
                    delay(2000)
                    showSplashScreen = false
                }

                if (showSplashScreen) {
                    // [ALTERAÇÃO] Chama o Composable do novo arquivo
                    SplashScreen()
                } else {
                    // [ALTERAÇÃO] Chama o Composable do novo arquivo
                    TimerScreen(
                        timerText = uiState.timerText,
                        isCapturing = uiState.isCapturing,
                        feedbackText = uiState.feedbackText,
                        onStartClick = { viewModel.startCapture() },
                        onStopClick = { viewModel.stopAndSendData() }
                    )
                }
            }
        }
    }
}