package com.ruinobrega.fastinator.ui.components
import com.ruinobrega.fastinator.debugTag

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ruinobrega.fastinator.ui.theme.OverTargetGold

/**
 * Large circular progress ring centerpiece for Fastinator.
 * Uses a strictly determinate circular progress indicator computed from elapsed / 13h target.
 */
@Composable
fun CircularFastingRing(
    isFasting: Boolean,
    elapsedFormatted: String,
    achievementPercentageFormatted: String,
    targetClockTime: String,
    progressFraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 270.dp,
    strokeWidth: Dp = 18.dp,
) {
    val achievementPercentage = achievementPercentageFormatted.replace("%", "").toFloatOrNull() ?: 0f
    val isOverTarget = achievementPercentage >= 100f
    val targetSweep = (progressFraction.coerceIn(0f, 1f)) * 360f

    val animatedSweep by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = tween(durationMillis = 800),
        label = "sweepAnimation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val accentColor by animateColorAsState(
        targetValue = if (isOverTarget) OverTargetGold else primaryColor,
        animationSpec = tween(durationMillis = 500),
        label = "accentColorAnim"
    )

    val semanticDescription = if (isFasting) {
        "Fasting progress ring. Elapsed time: $elapsedFormatted. Achievement: $achievementPercentageFormatted against fixed 13-hour target. Target reached at $targetClockTime."
    } else {
        "Fasting progress ring idle. Fixed target: 13 hours. Target reached at $targetClockTime."
    }

    Box(
        modifier = modifier
            .size(size)
            .debugTag("circular_progress")
            .semantics(mergeDescendants = true) {
                contentDescription = semanticDescription
            },
        contentAlignment = Alignment.Center
    ) {
        // Custom determinate progress canvas
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(size.toPx() - strokePx, size.toPx() - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)

            // Background Track
            drawArc(
                color = trackColor.copy(alpha = 0.6f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (isFasting && animatedSweep > 0f) {
                // Gradient arc for active fasting
                val gradientBrush = Brush.sweepGradient(
                    colors = if (isOverTarget) {
                        listOf(primaryColor, OverTargetGold, tertiaryColor)
                    } else {
                        listOf(primaryColor, tertiaryColor)
                    }
                )

                drawArc(
                    brush = gradientBrush,
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Additional golden halo for > 100% bonus fasting
                if (isOverTarget) {
                    drawArc(
                        color = OverTargetGold.copy(alpha = 0.35f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(strokePx / 2 - 4.dp.toPx(), strokePx / 2 - 4.dp.toPx()),
                        size = Size(arcSize.width + 8.dp.toPx(), arcSize.height + 8.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Center Content Column
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elapsed time in bold HH:MM
            Text(
                text = elapsedFormatted,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Achievement Percentage (always displayed, can exceed 100%)
            Text(
                text = achievementPercentageFormatted,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Clock time (HH:MM) at which the 13h target is / was reached (spec §3.1)
            if (targetClockTime.isNotBlank()) {
                Text(
                    text = "13h at $targetClockTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
