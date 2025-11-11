package com.tcc.monitorrcp.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.R
import com.tcc.monitorrcp.model.TestResult
import com.tcc.monitorrcp.ui.components.QualityRow
import com.tcc.monitorrcp.ui.components.ResultMetric

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    result: TestResult?,
    intermediateFeedback: String,
    onBack: () -> Unit
) {
    val feedbackColor = when {
        intermediateFeedback.contains("✅") -> Color(0xFF66BB6A) // Verde mais forte
        intermediateFeedback.contains("⚠️") -> Color(0xFFFFEE58) // Amarelo mais forte
        else -> MaterialTheme.colorScheme.surface // Cor Padrão
    }

    val contentColor = when {
        intermediateFeedback.contains("✅") || intermediateFeedback.contains("⚠️") -> Color.Black
        else -> MaterialTheme.colorScheme.primary
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (result == null) "Teste em Andamento" else "Resultados do Teste") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = feedbackColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(feedbackColor)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (result != null) {
                    // --- LAYOUT DE RESULTADO FINAL ---

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ResultMetric(
                            label = "Frequência Mediana",
                            value = "%.0f".format(result.medianFrequency),
                            unit = "cpm",
                            modifier = Modifier.weight(1f)
                        )
                        ResultMetric(
                            label = "Profundidade Média",
                            value = "%.1f".format(result.averageDepth),
                            unit = "cm",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Análise de Qualidade",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // [CORREÇÃO] Adicionada a Duração do Teste
                            QualityRow("Duração do Teste:", result.formattedDuration)
                            QualityRow("Total de Compressões:", "${result.totalCompressions}")

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            QualityRow("Compressões (Freq. Correta):", "${result.correctFrequencyCount} de ${result.totalCompressions}")
                            QualityRow("Compressões (Prof. Correta):", "${result.correctDepthCount} de ${result.totalCompressions}")
                        }
                    }

                } else {
                    // --- TESTE EM ANDAMENTO COM UI MELHORADA ---

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse-scale"
                        )

                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = "Testando",
                            modifier = Modifier
                                .size(120.dp)
                                .scale(scale),
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = intermediateFeedback,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = contentColor
                        )
                        Text(
                            text = "Continue as compressões...",
                            fontSize = 18.sp,
                            color = contentColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(48.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(60.dp),
                            strokeWidth = 5.dp,
                            color = contentColor
                        )
                    }
                }
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
}