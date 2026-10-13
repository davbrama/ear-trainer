package dev.eartrainer.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.eartrainer.core.model.Direction
import dev.eartrainer.core.model.Phase
import dev.eartrainer.ui.theme.Brass
import dev.eartrainer.ui.theme.Mist

@Composable
fun IntervalCompass(
    phase: Phase,
    direction: Direction?,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale",
    )
    val active = phase == Phase.PLAYING || phase == Phase.AWAITING_ANSWER
    Canvas(modifier.fillMaxWidth().aspectRatio(1.15f)) {
        val ground = Mist.copy(alpha = 0.35f)
        val ink = Brass
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * 0.38f
        drawCircle(ground, radius, Offset(cx, cy), style = Stroke(width = 2f))
        val lift = when (direction) {
            Direction.ASCENDING -> -radius * 0.28f
            Direction.DESCENDING -> radius * 0.28f
            else -> 0f
        }
        val left = Offset(cx - radius * 0.42f, cy + lift * 0.15f)
        val right = Offset(cx + radius * 0.42f, cy + lift)
        drawLine(ink.copy(alpha = 0.7f), left, right, strokeWidth = 4f, cap = StrokeCap.Round)
        val grow = if (active) pulse else 1f
        drawCircle(ink, 18f * grow, left)
        drawCircle(if (direction == Direction.HARMONIC) ink else ink.copy(alpha = 0.85f), 14f * grow, right)
        if (phase == Phase.AWAITING_ANSWER) {
            drawCircle(Color.White.copy(alpha = 0.9f), 5f, right)
        }
    }
}
