package com.you.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

@Composable
fun GlyphPlay(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.2f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.2f, h * 0.9f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun GlyphPause(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(color, Offset(w * 0.2f, h * 0.1f), Size(w * 0.22f, h * 0.8f))
        drawRect(color, Offset(w * 0.58f, h * 0.1f), Size(w * 0.22f, h * 0.8f))
    }
}

@Composable
fun GlyphNext(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.1f, h * 0.15f)
            lineTo(w * 0.7f, h * 0.5f)
            lineTo(w * 0.1f, h * 0.85f)
            close()
        }
        drawPath(p, color)
        drawRect(color, Offset(w * 0.78f, h * 0.15f), Size(w * 0.12f, h * 0.7f))
    }
}

@Composable
fun GlyphPrev(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.9f, h * 0.15f)
            lineTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.9f, h * 0.85f)
            close()
        }
        drawPath(p, color)
        drawRect(color, Offset(w * 0.1f, h * 0.15f), Size(w * 0.12f, h * 0.7f))
    }
}

@Composable
fun GlyphFullscreen(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    exit: Boolean = false
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val m = w * 0.12f
        val l = w * 0.3f
        val sw = w * 0.1f
        if (!exit) {
            drawLine(color, Offset(m, m), Offset(m + l, m), sw)
            drawLine(color, Offset(m, m), Offset(m, m + l), sw)
            drawLine(color, Offset(w - m, m), Offset(w - m - l, m), sw)
            drawLine(color, Offset(w - m, m), Offset(w - m, m + l), sw)
            drawLine(color, Offset(m, h - m), Offset(m + l, h - m), sw)
            drawLine(color, Offset(m, h - m), Offset(m, h - m - l), sw)
            drawLine(color, Offset(w - m, h - m), Offset(w - m - l, h - m), sw)
            drawLine(color, Offset(w - m, h - m), Offset(w - m, h - m - l), sw)
        } else {
            drawLine(color, Offset(m + l, m), Offset(m + l, m + l), sw)
            drawLine(color, Offset(m + l, m + l), Offset(m, m + l), sw)
            drawLine(color, Offset(w - m - l, m), Offset(w - m - l, m + l), sw)
            drawLine(color, Offset(w - m - l, m + l), Offset(w - m, m + l), sw)
            drawLine(color, Offset(m + l, h - m), Offset(m + l, h - m - l), sw)
            drawLine(color, Offset(m + l, h - m - l), Offset(m, h - m - l), sw)
            drawLine(color, Offset(w - m - l, h - m), Offset(w - m - l, h - m - l), sw)
            drawLine(color, Offset(w - m - l, h - m - l), Offset(w - m, h - m - l), sw)
        }
    }
}
