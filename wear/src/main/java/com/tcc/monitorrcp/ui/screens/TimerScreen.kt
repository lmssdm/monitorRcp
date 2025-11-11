package com.tcc.monitorrcp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text

@Composable
fun TimerScreen(
    timerText: String,
    isCapturing: Boolean,
    feedbackText: String,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF616161),
                        Color(0xFF212121)
                    )
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cronômetro
        Text(
            text = timerText,
            fontSize = 52.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        // Feedback em tempo real
        Text(
            text = feedbackText,
            color = when {
                feedbackText.contains("Perfeito", ignoreCase = true) -> Color(0xFF4CAF50)
                feedbackText.contains("rápido", ignoreCase = true) -> Color(0xFFFFEB3B)
                feedbackText.contains("lento", ignoreCase = true) -> Color(0xFFFFEB3B)
                else -> Color.LightGray
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Botões de controle
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
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
            ) {
                Text("INICIAR", color = Color.White, fontSize = 11.sp)
            }
            Spacer(Modifier.width(20.dp))
            Button(
                onClick = onStopClick,
                enabled = isCapturing,
                modifier = Modifier.size(70.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF44336))
            ) {
                Text("PARAR", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}