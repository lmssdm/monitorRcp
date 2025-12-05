package com.tcc.monitorrcp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.R
// --- [MUDANÇA AQUI] Importa o novo componente ---
import com.tcc.monitorrcp.ui.components.AppWatermark
import com.tcc.monitorrcp.ui.components.InstructionPage
import com.tcc.monitorrcp.ui.components.InstructionStepData

/**
 * Um carrossel educativo ensinando como fazer RCP corretamente.
 */
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { pageIndex ->
                    InstructionPage(step = steps[pageIndex])
                }

                Row(
                    Modifier
                        .height(50.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
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
            AppWatermark()
        }
    }
}