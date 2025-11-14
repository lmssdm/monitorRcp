package com.tcc.monitorrcp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcc.monitorrcp.R

/**
 * Adiciona a logo e o slogan do app no canto inferior da tela.
 * Este Composable deve ser usado dentro de um Box.
 */
@Composable
fun BoxScope.AppWatermark() {
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