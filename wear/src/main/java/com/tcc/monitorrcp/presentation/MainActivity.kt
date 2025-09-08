package com.tcc.monitorrcp.presentation

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import com.tcc.monitorrcp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var capturedData = mutableListOf<String>()
    private var isCapturing by mutableStateOf(false)

    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val nodeClient by lazy { Wearable.getNodeClient(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.values.all { it }) {
            Toast.makeText(this, "Permissões são necessárias.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher.launch(arrayOf(Manifest.permission.BODY_SENSORS, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            var showSplashScreen by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(2000) // Mostra a splash screen por 2 segundos
                showSplashScreen = false
            }

            if (showSplashScreen) {
                SplashScreen()
            } else {
                TimerScreen(
                    onStartClick = { startCapture() },
                    onStopClick = { stopAndSendData() },
                    isCapturing = isCapturing
                )
            }
        }
    }

    private fun startCapture() {
        if (!isCapturing) {
            capturedData.clear()
            capturedData.add("Timestamp,Type,X,Y,Z") // Header CSV
            isCapturing = true
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI)
            Toast.makeText(this, "Captura iniciada!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAndSendData() {
        if (isCapturing) {
            isCapturing = false
            sensorManager.unregisterListener(this)
            Toast.makeText(this, "Captura parada. A enviar...", Toast.LENGTH_SHORT).show()
            sendSensorData()
        }
    }

    private fun sendSensorData() {
        if (capturedData.size <= 1) {
            Toast.makeText(this, "Nenhum dado capturado.", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Falha: Telemóvel não encontrado.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val nodeId = nodes.first().id
                val dataToSend = capturedData.joinToString("\n").toByteArray(Charsets.UTF_8)

                messageClient.sendMessage(nodeId, "/sensor_data", dataToSend).apply {
                    addOnSuccessListener { Toast.makeText(this@MainActivity, "Dados enviados!", Toast.LENGTH_LONG).show() }
                    addOnFailureListener { Toast.makeText(this@MainActivity, "Falha ao enviar.", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (isCapturing && event != null) {
            val timestamp = System.currentTimeMillis()
            val type = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) "ACC" else "GYR"
            val values = event.values
            capturedData.add("$timestamp,$type,${values[0]},${values[1]},${values[2]}")
        }
    }
}

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
            painter = painterResource(id = R.drawable.logo_rcp), // Usa a sua imagem
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
fun TimerScreen(onStartClick: () -> Unit, onStopClick: () -> Unit, isCapturing: Boolean) {
    var elapsedTime by remember { mutableStateOf(0L) }

    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            val startTime = System.currentTimeMillis()
            while (isCapturing) {
                elapsedTime = System.currentTimeMillis() - startTime
                delay(100) // Atualiza a cada 100ms
            }
        } else {
            elapsedTime = 0L
        }
    }

    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60

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
            text = String.format("%02d:%02d", minutes, seconds),
            fontSize = 52.sp, // Tamanho final do texto
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp)) // Espaço ligeiramente reduzido
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onStartClick,
                enabled = !isCapturing,
                modifier = Modifier.size(70.dp), // Botão final, um pouco menor
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)) // Verde
            ) {
                Text("INICIAR", color = Color.White, fontSize = 11.sp) // Fonte ligeiramente menor
            }
            Spacer(Modifier.width(20.dp)) // Espaço ligeiramente aumentado
            Button(
                onClick = onStopClick,
                enabled = isCapturing,
                modifier = Modifier.size(70.dp), // Botão final, um pouco menor
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF44336)) // Vermelho
            ) {
                Text("PARAR", color = Color.White, fontSize = 11.sp) // Fonte ligeiramente menor
            }
        }
    }
}