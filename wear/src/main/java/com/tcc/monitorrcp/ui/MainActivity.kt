package com.tcc.monitorrcp.ui

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.tcc.monitorrcp.R
import com.tcc.monitorrcp.presentation.theme.MonitorRcpTheme // Você pode precisar ajustar este import
import com.tcc.monitorrcp.ui.viewmodel.WearViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    // Instancia o nosso novo ViewModel
    private val viewModel: WearViewModel by viewModels()

    // O lançador de permissões continua aqui, pois é parte do ciclo de vida da Activity
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.values.all { it }) {
            Toast.makeText(this, "Permissões são necessárias.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(arrayOf(
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ))

        setContent {
            MonitorRcpTheme {
                // Coleta o estado do ViewModel. A UI vai se redesenhar sempre que o estado mudar.
                val uiState by viewModel.uiState.collectAsState()
                var showSplashScreen by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(2000)
                    showSplashScreen = false
                }

                if (showSplashScreen) {
                    SplashScreen()
                } else {
                    TimerScreen(
                        timerText = uiState.timerText,
                        isCapturing = uiState.isCapturing,
                        onStartClick = { viewModel.startCapture() },
                        onStopClick = { viewModel.stopAndSendData() }
                    )
                }
            }
        }
    }
}

// --- COMPOSABLES ---
// As telas agora são "burras", elas apenas recebem dados e os exibem.

@Composable
fun SplashScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_rcp),
            contentDescription = "Logo RCP",
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "SISTEMA PARA\nANÁLISE RCP",
            color = Color.Black,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator()
    }
}

@Composable
fun TimerScreen(
    timerText: String,
    isCapturing: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF616161), // Cinza médio
                        Color(0xFF212121)  // Cinza escuro
                    )
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timerText,
            fontSize = 52.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStartClick,
                enabled = !isCapturing,
                modifier = Modifier.size(70.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)) // Verde
            ) {
                Text("INICIAR", color = Color.White, fontSize = 11.sp)
            }
            Spacer(Modifier.width(20.dp))
            Button(
                onClick = onStopClick,
                enabled = isCapturing,
                modifier = Modifier.size(70.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF44336)) // Vermelho
            ) {
                Text("PARAR", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}