package com.tcc.monitorrcp.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.R
import com.tcc.monitorrcp.model.TestResult
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.TextFieldDefaults

@Composable
fun HomeScreen(
    name: String,
    onStartClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onInstructionsClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("INICIAR TESTE", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onHistoryClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text("HISTÓRICO", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onInstructionsClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Text("COMO REALIZAR RCP", fontSize = 16.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))
        }

        Image(
            painter = painterResource(id = R.drawable.logo_rcp),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(120.dp)
                .alpha(0.3f)
        )

        Text(
            text = "Você ajuda pessoas.\nNós ajudamos você!",
            textAlign = TextAlign.Start,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 32.dp)
        )
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLogin: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
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
            Text(
                "Informe seu nome:",
                modifier = Modifier.fillMaxWidth(),
                color = Color.DarkGray
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
               // placeholder = { Text("ex. Luiz Michel") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { onLogin(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) {
                Text("ENTRAR", fontSize = 16.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        Image(
            painter = painterResource(id = R.drawable.logo_rcp),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(150.dp)
                .alpha(0.3f)
        )

        Text(
            text = "Você ajuda pessoas.\nNós ajudamos você!",
            textAlign = TextAlign.Start,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 32.dp)
        )
    }
}

// -----------------------------------------------------------------
// TELA DE INSTRUÇÕES (CARROSSEL)
// -----------------------------------------------------------------

data class InstructionStepData(
    @DrawableRes val imageRes: Int,
    val title: String,
    val text: String
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(onBack: () -> Unit) {

    val steps = remember {
        listOf(
            InstructionStepData(
                imageRes = R.drawable.passo1,
                title = "Passo 1:",
                text = "Posicione a vítima de costas em uma superfície rígida e plana."
            ),
            InstructionStepData(
                imageRes = R.drawable.passo2,
                title = "Passo 2:",
                text = "Coloque as mãos sobrepostas no centro do peito da vítima."
            ),
            InstructionStepData(
                imageRes = R.drawable.passo3,
                title = "Passo 3:",
                text = "Mantenha os braços esticados e use o peso do seu corpo para comprimir."
            ),
            InstructionStepData(
                imageRes = R.drawable.passo4,
                title = "Passo 4:",
                text = "Comprima o tórax a uma profundidade de 5 a 6 centímetros."
            ),
            InstructionStepData(
                imageRes = R.drawable.passo5,
                title = "Passo 5:",
                text = "Mantenha um ritmo de 100 a 120 compressões por minuto."
            ),
            InstructionStepData(
                imageRes = R.drawable.passo6,
                title = "Passo 6:",
                text = "Permita o retorno completo do tórax entre cada compressão."
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { steps.size })

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
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                InstructionPage(step = steps[pageIndex])
            }

            // Indicadores (pontinhos)
            Row(
                Modifier
                    .height(50.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(steps.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(12.dp)
                    )
                }
            }
        }
    }
}

// Composable para desenhar cada PÁGINA do carrossel
@Composable
fun InstructionPage(step: InstructionStepData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = step.imageRes),
            contentDescription = step.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = step.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// -----------------------------------------------------------------
// RESTANTE DO ARQUIVO
// -----------------------------------------------------------------

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhum teste realizado ainda.")
                }
            } else {
                Text("Resultados Anteriores", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    LineChart(
                        data = history.map { it.medianFrequency },
                        label = "Evolução da Frequência (cpm)",
                        color = Color(0xFF1565C0)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    LineChart(
                        data = history.map { it.averageDepth },
                        label = "Evolução da Profundidade (cm)",
                        color = Color(0xFF2E7D32)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Lista Detalhada", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))

                history.forEach { result ->
                    HistoryItem(result)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun LineChart(data: List<Double>, label: String, color: Color) {
    val dataPoints = data.takeLast(10)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            if (dataPoints.size > 1) {
                val reversedDataPoints = dataPoints.reversed()
                val stepX = size.width / (reversedDataPoints.size - 1)
                val minY = reversedDataPoints.minOrNull()?.toFloat() ?: 0f
                val maxY = reversedDataPoints.maxOrNull()?.toFloat() ?: 1f
                val rangeY = (maxY - minY).takeIf { it > 0f } ?: 1f

                for (i in 0 until reversedDataPoints.size - 1) {
                    val startX = i * stepX
                    val startY = size.height * (1 - ((reversedDataPoints[i].toFloat() - minY) / rangeY))
                    val endX = (i + 1) * stepX
                    val endY = size.height * (1 - ((reversedDataPoints[i + 1].toFloat() - minY) / rangeY))
                    drawLine(color = color, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 5f)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    result: TestResult?,
    intermediateFeedback: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (result == null) "Teste em Andamento" else "Resultados do Teste") },
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
                // --- [MUDANÇA] NOVO LAYOUT DE RESULTADO ---

                // Métricas Principais Lado a Lado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp) // Espaço entre os cards
                ) {
                    ResultMetric(
                        label = "Frequência Mediana",
                        value = "%.0f".format(result.medianFrequency),
                        unit = "cpm",
                        modifier = Modifier.weight(1f) // Ocupa metade
                    )
                    ResultMetric(
                        label = "Profundidade Média",
                        value = "%.1f".format(result.averageDepth),
                        unit = "cm",
                        modifier = Modifier.weight(1f) // Ocupa metade
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Card de Análise de Qualidade
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Análise de Qualidade",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        QualityRow("Total de Compressões:", "${result.totalCompressions}")

                        // Divisor para separar o total do resto
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        QualityRow("Compressões (Freq. Correta):", "${result.correctFrequencyCount} de ${result.totalCompressions}")
                        QualityRow("Compressões (Prof. Correta):", "${result.correctDepthCount} de ${result.totalCompressions}")
                    }
                }
            } else {
                // --- TESTE EM ANDAMENTO (Sem mudança) ---
                Spacer(modifier = Modifier.height(64.dp))
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = "Testando",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = intermediateFeedback,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Continue as compressões...",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
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

// [MUDANÇA] Função ResultMetric agora é um Card
@Composable
fun ResultMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall, // Tamanho grande
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HistoryItem(result: TestResult) {
    val date = remember(result.timestamp) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(result.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text("Data: $date", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Frequência Mediana: %.1f cpm".format(result.medianFrequency), fontSize = 14.sp)
        Text("Profundidade Média: %.1f cm".format(result.averageDepth), fontSize = 14.sp)
        Text("Total de Compressões: ${result.totalCompressions}", fontSize = 14.sp)
    }
}