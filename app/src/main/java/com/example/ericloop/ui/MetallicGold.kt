package com.example.ericloop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Static brushed gold: cache brushes and grain paths until size or theme changes. */
@Composable fun Modifier.metallicGold(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val dark = isSystemInDarkTheme()
    val tones = if (dark) listOf(Color(0xFF45351C), Color(0xFF6B5630), Color(0xFF514126), Color(0xFF61502F))
        else listOf(Color(0xFFCDAE70), Color(0xFFF0DFAD), Color(0xFFDDC48A), Color(0xFFE6D09C))
    return drawWithCache {
        val base = Brush.linearGradient(
            0f to tones[0], 0.32f to tones[1], 0.65f to tones[2], 1f to tones[3],
            start = Offset.Zero, end = Offset(size.width, size.height * 0.8f),
        )
        val highlight = Brush.linearGradient(
            0f to Color.Transparent, 0.42f to Color.White.copy(alpha = if (dark) 0.025f else 0.07f), 1f to Color.Transparent,
            start = Offset(size.width * 0.1f, 0f), end = Offset(size.width * 0.85f, size.height),
        )
        val random = Random(41)
        val lightGrain = Path()
        val darkGrain = Path()
        val count = (size.width * size.height / (density * density * 28f)).toInt().coerceIn(80, 2400)
        repeat(count) { index ->
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val path = if (index % 2 == 0) lightGrain else darkGrain
            path.moveTo(x, y)
            path.lineTo((x + (0.4f + random.nextFloat()) * density).coerceAtMost(size.width), y)
        }
        val grainStroke = Stroke(width = 0.3.dp.toPx())
        onDrawBehind {
            drawRect(base)
            drawRect(highlight)
            drawPath(lightGrain, Color.White.copy(alpha = 0.09f), style = grainStroke)
            drawPath(darkGrain, Color(0xFF624A1F).copy(alpha = if (dark) 0.12f else 0.055f), style = grainStroke)
        }
    }
}
