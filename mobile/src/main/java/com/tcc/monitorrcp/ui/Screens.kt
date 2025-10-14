package com.tcc.monitorrcp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.R
import com.tcc.monitorrcp.model.TestResult
import kotlinx.coroutines.delay

// 🏠 HomeScreen
@Composable
fun HomeScreen(
    name: String,
    onStartClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onInstructionsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Image(
            painter = painterResource(id = R.drawable.logo_rcp),
            contentDescription = "Logo RCP",
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Olá, $name!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Bem-vindo ao SPAR!", fontSize = 16.sp, color = Color.Gray)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStartClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
        ) {
            Text("INICIAR TESTE", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onHistoryClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
        ) {
            Text("HISTÓRICO", fontSize = 16.sp, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onInstructionsClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0277BD))
        ) {
            Text("COMO REALIZAR RCP", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Você ajuda pessoas.\nNós ajudamos você!",
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// 🚀 SplashScreen
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2500)
        onTimeout()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_rcp),
            contentDescription = "Logo RCP",
            modifier = Modifier.size(150.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "SISTEMA PARA ANÁLISE RCP",
            color = Color.Black,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "(Sistema para Análise de Testes de Ressuscitação Cardiopulmonar)",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator(color = Color.DarkGray)
        Spacer(Modifier.height(8.dp))
        Text(text = "Aguarde...", color = Color.Gray, fontSize = 14.sp)
    }
}

// 🔑 LoginScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLogin: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .background(Color(0xFFF5F5F5)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(50.dp))
        Image(
            painter = painterResource(id = R.drawable.logo_rcp),
            contentDescription = "Logo RCP",
            modifier = Modifier.size(120.dp)
        )
        Text(
            text = "SISTEMA PARA ANÁLISE RCP",
            color = Color.Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "(Sistema para Análise de Testes de Ressuscitação Cardiopulmonar)",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(60.dp))
        Text("Informe seu nome:", modifier = Modifier.fillMaxWidth(), color = Color.DarkGray)

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

// 📘 InstructionsScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instruções de RCP") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Como realizar compressões corretas?", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            InstructionStep(Icons.Default.Info, "Passo 1:", "Posicione a vítima de costas em uma superfície rígida e plana.")
            InstructionStep(Icons.Default.Info, "Passo 2:", "Coloque as mãos sobrepostas no centro do peito da vítima.")
            InstructionStep(Icons.Default.Info, "Passo 3:", "Mantenha os braços esticados e use o peso do seu corpo para comprimir.")
            InstructionStep(Icons.Default.RadioButtonChecked, "Passo 4:", "Comprima o tórax a uma profundidade de 5 a 6 centímetros.")
            InstructionStep(Icons.Default.PlayCircleFilled, "Passo 5:", "Mantenha um ritmo de 100 a 120 compressões por minuto.")
            InstructionStep(Icons.Default.CheckCircle, "Passo 6:", "Permita o retorno completo do tórax entre cada compressão.")
        }
    }
}

@Composable
fun InstructionStep(icon: ImageVector, number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).padding(end = 16.dp)
        )
        Column {
            Text(number, fontWeight = FontWeight.Bold)
            Text(text)
        }
    }
}

// 📊 HistoryScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(history: List<TestResult>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Testes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum teste realizado ainda.")
                }
            } else {
                Text("Resultados Anteriores", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                LineChart(
                    data = history.map { it.medianFrequency },
                    label = "Evolução da Frequência (cpm)",
                    color = Color(0xFF1565C0)
                )
                Spacer(modifier = Modifier.height(24.dp))
                LineChart(
                    data = history.map { it.averageDepth },
                    label = "Evolução da Profundidade (cm)",
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun LineChart(data: List<Double>, label: String, color: Color) {
    val dataPoints = data.takeLast(10)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.LightGray.copy(alpha = 0.1f))
        ) {
            if (dataPoints.size > 1) {
                val stepX = size.width / (dataPoints.size - 1)
                val minY = dataPoints.minOrNull()?.toFloat() ?: 0f
                val maxY = dataPoints.maxOrNull()?.toFloat() ?: 1f
                val rangeY = if (maxY - minY == 0f) 1f else maxY - minY

                for (i in 0 until dataPoints.size - 1) {
                    val startX = i * stepX
                    val startY = size.height * (1 - ((dataPoints[i].toFloat() - minY) / rangeY))
                    val endX = (i + 1) * stepX
                    val endY = size.height * (1 - ((dataPoints[i + 1].toFloat() - minY) / rangeY))
                    drawLine(color = color, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 5f)
                }
            }
        }
    }
}

// 📈 DataScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(result: TestResult?, data: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultados Detalhados") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (result != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ResultMetric("Frequência Mediana", "%.0f".format(result.medianFrequency), "cpm")
                    ResultMetric("Profundidade Média", "%.1f".format(result.averageDepth), "cm")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Análise de Qualidade", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        QualityRow("Total de Compressões:", "${result.totalCompressions}")
                        QualityRow("Compressões (Freq. Correta):", "${result.correctFrequencyCount} de ${result.totalCompressions}")
                        QualityRow("Compressões (Prof. Correta):", "${result.correctDepthCount} de ${result.totalCompressions}")
                    }
                }
            } else {
                Text("Aguardando teste do relógio...", fontSize = 18.sp)
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Dados Brutos Recebidos:", fontSize = 16.sp)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            ) {
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
