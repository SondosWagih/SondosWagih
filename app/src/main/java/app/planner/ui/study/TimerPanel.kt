package app.planner.ui.study

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.planner.R

/** Countdown ring with pause/resume and stop. */
@Composable
fun TimerPanel(
    title: String,
    remainingSeconds: Long,
    totalSeconds: Long,
    running: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progress = MaterialTheme.colorScheme.primary
    val fraction = if (totalSeconds > 0) (remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f

    Card(modifier.fillMaxWidth().testTag("timer_panel")) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke))
                    drawArc(
                        color = progress, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                        topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text(formatClock(remainingSeconds), style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("timer_text"))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (running) FilledTonalButton(onClick = onPause) { Text(stringResource(R.string.pause)) }
                else Button(onClick = onResume) { Text(stringResource(R.string.resume)) }
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.stop)) }
            }
        }
    }
}
