package com.tcc.monitorrcp

import android.Manifest
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

// --- Estruturas de Dados e Navegação ---

enum class Screen {
    SplashScreen,
    LoginScreen,
    HomeScreen,
    DataScreen,
    HistoryScreen
}

data class SensorDataPoint(val timestamp: Long, val type: String, val x: Float, val y: Float, val z: Float) {
    fun magnitude(): Float = sqrt(x * x + y * y + z * z)
}

data class TestResult(
    val timestamp: Long,
    val medianFrequency: Double,
    val averageDepth: Double,
    val totalCompressions: Int,
    val correctFrequencyCount: Int,
    val correctDepthCount: Int
)

class MainActivity : ComponentActivity() {

    // Estados da aplicação
    private var lastDataString by mutableStateOf("A aguardar dados do relógio...")
    private var lastTestResult by mutableStateOf<TestResult?>(null)
    private var history by mutableStateOf<List<TestResult>>(emptyList())
    private var currentScreen by mutableStateOf(Screen.SplashScreen)
    private var userName by mutableStateOf<String?>(null)

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("data")?.let {
                lastDataString = it
                processAndAnalyzeData(it)
                saveDataToFile(it)
                currentScreen = Screen.DataScreen
            }
        }
    }

    // --- Lógica de Análise e Persistência ---

    private fun processAndAnalyzeData(csvData: String) {
        val dataPoints = parseData(csvData)
        if (dataPoints.isEmpty()) return

        val accelerometerData = dataPoints.filter { it.type == "ACC" }
        val accelerationMagnitudes = accelerometerData.map { it.magnitude() }
        val smoothedMagnitudes = movingAverage(accelerationMagnitudes, 3)

        // --- CALIBRAÇÃO: Novo ajuste para mais sensibilidade ---
        val minPeakHeight = 10.3f
        val minPeakDistance = 4

        val allPeakIndices = findPeaks(smoothedMagnitudes, minPeakHeight, minPeakDistance)

        val validPeakIndices = if (allPeakIndices.size > 2) {
            allPeakIndices.subList(1, allPeakIndices.size - 1)
        } else {
            allPeakIndices
        }
        val totalCompressions = validPeakIndices.size

        val individualFrequencies = calculateIndividualFrequencies(accelerometerData, validPeakIndices)
        val individualDepths = calculateIndividualDepths(accelerometerData, validPeakIndices)

        val correctFrequencyCount = individualFrequencies.count { it in 100.0..120.0 }
        val correctDepthCount = individualDepths.count { it in 4.0..6.0 }

        val medianFrequency = if (individualFrequencies.isNotEmpty()) individualFrequencies.median() else 0.0
        val averageDepth = if (individualDepths.isNotEmpty()) individualDepths.average() else 0.0

        val newResult = TestResult(System.currentTimeMillis(), medianFrequency, averageDepth, totalCompressions, correctFrequencyCount, correctDepthCount)
        lastTestResult = newResult
        saveNewResultToHistory(newResult)

        Log.d("RCP_ANALYSIS", "Resultado: ${newResult.totalCompressions} compressões (Freq Mediana: $medianFrequency, Prof Média: $averageDepth)")
    }

    private fun saveNewResultToHistory(newResult: TestResult) {
        val prefs = getSharedPreferences("RCP_HISTORY", Context.MODE_PRIVATE)
        val historyString = prefs.getString("results", "") ?: ""
        val newResultString = "${newResult.timestamp},${newResult.medianFrequency},${newResult.averageDepth},${newResult.totalCompressions},${newResult.correctFrequencyCount},${newResult.correctDepthCount}"

        val updatedHistory = if (historyString.isEmpty()) newResultString else "$historyString;$newResultString"
        prefs.edit().putString("results", updatedHistory).apply()
        loadHistory()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("RCP_HISTORY", Context.MODE_PRIVATE)
        val historyString = prefs.getString("results", "") ?: ""
        history = historyString.split(';')
            .filter { it.isNotBlank() }
            .mapNotNull {
                try {
                    val parts = it.split(',')
                    TestResult(parts[0].toLong(), parts[1].toDouble(), parts[2].toDouble(), parts[3].toInt(), parts[4].toInt(), parts[5].toInt())
                } catch (e: Exception) { null }
            }
            .sortedByDescending { it.timestamp }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) Toast.makeText(this, "Permissão é necessária.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadHistory()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataReceiver, IntentFilter("com.tcc.monitorrcp.DATA_RECEIVED"), RECEIVER_EXPORTED)
        } else {
            registerReceiver(dataReceiver, IntentFilter("com.tcc.monitorrcp.DATA_RECEIVED"))
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        Screen.SplashScreen -> SplashScreen(onTimeout = { currentScreen = Screen.LoginScreen })
                        Screen.LoginScreen -> LoginScreen(onLogin = { name -> userName = name; currentScreen = Screen.HomeScreen })
                        Screen.HomeScreen -> HomeScreen(
                            name = userName ?: "",
                            onStartClick = {
                                lastTestResult = null
                                lastDataString = "A aguardar novo teste do relógio..."
                                currentScreen = Screen.DataScreen
                            },
                            onHistoryClick = { currentScreen = Screen.HistoryScreen }
                        )
                        Screen.DataScreen -> DataScreen(
                            result = lastTestResult,
                            data = lastDataString,
                            onBack = { currentScreen = Screen.HomeScreen }
                        )
                        Screen.HistoryScreen -> HistoryScreen(
                            history = history,
                            onBack = { currentScreen = Screen.HomeScreen }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); unregisterReceiver(dataReceiver) }

    private fun saveDataToFile(data: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "dados_rcp_$timestamp.txt"

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) }
                Toast.makeText(this, "Ficheiro guardado em Downloads!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao guardar ficheiro: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseData(csvData: String): List<SensorDataPoint> {
        return csvData.split("\n").drop(1).mapNotNull { line ->
            try {
                val parts = line.split(",")
                if (parts.size == 5) {
                    SensorDataPoint(parts[0].toLong(), parts[1], parts[2].toFloat(), parts[3].toFloat(), parts[4].toFloat())
                } else { null }
            } catch (e: Exception) { null }
        }
    }

    private fun movingAverage(data: List<Float>, windowSize: Int): List<Float> {
        if (windowSize <= 1) return data
        val result = mutableListOf<Float>()
        for (i in data.indices) {
            val window = data.subList(maxOf(0, i - windowSize + 1), i + 1)
            result.add(window.average().toFloat())
        }
        return result
    }

    private fun findPeaks(data: List<Float>, minHeight: Float, minDistance: Int): List<Int> {
        val peaks = mutableListOf<Int>()
        var i = 1
        while (i < data.size - 1) {
            if (data[i] > data[i - 1] && data[i] > data[i + 1] && data[i] >= minHeight) {
                if (peaks.isEmpty() || i - peaks.last() >= minDistance) {
                    peaks.add(i)
                }
            }
            i++
        }
        return peaks
    }

    private fun calculateIndividualFrequencies(accelerometerData: List<SensorDataPoint>, peakIndices: List<Int>): List<Double> {
        if (peakIndices.size < 2) return emptyList()
        return (0 until peakIndices.size - 1).map { i ->
            val startTime = accelerometerData[peakIndices[i]].timestamp
            val endTime = accelerometerData[peakIndices[i+1]].timestamp
            val duration = (endTime - startTime) / 1000.0
            if (duration > 0) 60.0 / duration else 0.0
        }
    }

    private fun calculateIndividualDepths(accelerometerData: List<SensorDataPoint>, peakIndices: List<Int>): List<Double> {
        if (peakIndices.size < 2) return emptyList()
        val alpha = 0.8f
        var gravityZ = 0f
        val linearAccelerationZ = accelerometerData.map {
            gravityZ = alpha * gravityZ + (1 - alpha) * it.z
            it.z - gravityZ
        }
        return (0 until peakIndices.size - 1).map { i ->
            val cycleAcceleration = linearAccelerationZ.slice(peakIndices[i]..peakIndices[i+1])
            val cycleTimestamps = accelerometerData.slice(peakIndices[i]..peakIndices[i+1]).map { it.timestamp }
            var velocity = 0.0
            var depth = 0.0
            var maxDepth = 0.0
            for (j in 0 until cycleAcceleration.size - 1) {
                val dt = (cycleTimestamps[j+1] - cycleTimestamps[j]) / 1000.0
                velocity += cycleAcceleration[j] * dt
                depth += velocity * dt
                if (abs(depth) > maxDepth) maxDepth = abs(depth)
            }
            maxDepth * 100
        }.filter { it > 1.0 && it < 10.0 }
    }

    fun List<Double>.median(): Double {
        if (this.isEmpty()) return 0.0
        val sorted = this.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }
}

// --- COMPOSABLES PARA CADA TELA ---

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2500)
        onTimeout()
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painter = painterResource(id = R.drawable.logo_rcp), contentDescription = "Logo RCP", modifier = Modifier.size(150.dp))
        Spacer(Modifier.height(8.dp))
        Text(text = "SISTEMA PARA ANÁLISE RCP", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = "(Sistema para Análise de Testes de Ressuscitação Cardiopulmonar)", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator(color = Color.DarkGray)
        Spacer(Modifier.height(8.dp))
        Text(text = "Aguarde...", color = Color.Gray, fontSize = 14.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLogin: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color(0xFFF5F5F5)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(50.dp))
        Image(painter = painterResource(id = R.drawable.logo_rcp), contentDescription = "Logo RCP", modifier = Modifier.size(120.dp))
        Text(text = "SISTEMA PARA ANÁLISE RCP", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = "(Sistema para Análise de Testes de Ressuscitação Cardiopulmonar)", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(60.dp))

        Text(text = "Informe seu nome:", modifier = Modifier.fillMaxWidth(), color = Color.DarkGray)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("ex: Luiz Michel") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onLogin(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
        ) {
            Text("ENTRAR", fontSize = 16.sp)
        }
    }
}

@Composable
fun HomeScreen(name: String, onStartClick: () -> Unit, onHistoryClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Image(painter = painterResource(id = R.drawable.logo_rcp), contentDescription = "Logo RCP", modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Olá, $name!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Bem-vindo ao SPAR!", fontSize = 16.sp, color = Color.Gray)

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onStartClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
        ) {
            Text("INICIAR TESTE", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onHistoryClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
        ) {
            Text("HISTÓRICO", fontSize = 16.sp, color = Color.Black)
        }

        Spacer(modifier = Modifier.weight(1f))
        Text("Você ajuda pessoas.\nNós ajudamos você!", textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(history: List<TestResult>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Testes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum teste realizado ainda.")
                }
            } else {
                Text("Resultados Anteriores", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                LineChart(data = history.map { it.medianFrequency }, label = "Evolução da Frequência (cpm)", color = Color.Blue)
                Spacer(modifier = Modifier.height(24.dp))
                LineChart(data = history.map { it.averageDepth }, label = "Evolução da Profundidade (cm)", color = Color.Green)
            }
        }
    }
}


@Composable
fun LineChart(data: List<Double>, label: String, color: Color) {
    val dataPoints = data.take(10).reversed()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.LightGray.copy(alpha = 0.1f))) {
            if (dataPoints.size > 1) {
                val stepX = size.width / (dataPoints.size - 1)
                val minY = dataPoints.minOrNull()?.toFloat() ?: 0f
                val maxY = dataPoints.maxOrNull()?.toFloat() ?: 1f
                val rangeY = if (maxY - minY == 0f) 1f else maxY - minY

                for (i in 0 until dataPoints.size - 1) {
                    val startX = i * stepX
                    val startY = size.height * (1 - ((dataPoints[i].toFloat() - minY) / rangeY))
                    val endX = (i + 1) * stepX
                    val endY = size.height * (1 - ((dataPoints[i+1].toFloat() - minY) / rangeY))

                    drawLine(color = color, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 5f)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(result: TestResult?, data: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultados Detalhados") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if(result != null) {
                // Painel de Médias
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    ResultMetric("Frequência Mediana", "%.0f".format(result.medianFrequency), "cpm")
                    ResultMetric("Profundidade Média", "%.1f".format(result.averageDepth), "cm")
                }
                Spacer(modifier = Modifier.height(24.dp))

                // PAINEL DE ANÁLISE DE QUALIDADE
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Análise de Qualidade", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        QualityRow("Total de Compressões:", "${result.totalCompressions}")
                        QualityRow("Compressões (Frequência Correta):", "${result.correctFrequencyCount} de ${result.totalCompressions}")
                        QualityRow("Compressões (Profundidade Correta):", "${result.correctDepthCount} de ${result.totalCompressions}")
                    }
                }

            } else {
                Text("Aguardando teste do relógio...", fontSize = 18.sp)
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Dados Brutos Recebidos:", fontSize = 16.sp)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Box(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Text(data, fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun QualityRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ResultMetric(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 16.sp, color = Color.Gray)
        Text(value, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(unit, fontSize = 14.sp)
    }
}

