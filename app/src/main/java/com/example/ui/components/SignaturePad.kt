package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun SignaturePad(
    modifier: Modifier = Modifier,
    pointsString: String,
    onSignatureChanged: (String) -> Unit
) {
    // Deserialize pointsString to Local list of lines
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }
    
    // Parse existing signature lines if present
    val lines = remember(pointsString) {
        if (pointsString.isEmpty()) {
            emptyList<List<Offset>>()
        } else {
            try {
                pointsString.split("|").map { lineStr ->
                    lineStr.split(";").mapNotNull { pStr ->
                        val parts = pStr.split(",")
                        if (parts.size == 2) {
                            Offset(parts[0].toFloat(), parts[1].toFloat())
                        } else null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    // Keep active lines in state for real-time rendering during draw
    val activeLines = remember { mutableStateListOf<List<Offset>>() }
    
    LaunchedEffect(lines) {
        activeLines.clear()
        activeLines.addAll(lines)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Scale down coordinate relative to Canvas size
                            val relativeX = offset.x / size.width
                            val relativeY = offset.y / size.height
                            currentLine = listOf(Offset(relativeX, relativeY))
                            activeLines.add(currentLine)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val rawOffset = change.position
                            val relativeX = (rawOffset.x / canvasWidth).coerceIn(0f, 1f)
                            val relativeY = (rawOffset.y / canvasHeight).coerceIn(0f, 1f)
                            
                            val newPoint = Offset(relativeX, relativeY)
                            currentLine = currentLine + newPoint
                            if (activeLines.isNotEmpty()) {
                                activeLines[activeLines.lastIndex] = currentLine
                            }
                        },
                        onDragEnd = {
                            // Serialize points back to string
                            val serialized = activeLines.joinToString("|") { line ->
                                line.joinToString(";") { "${it.x},${it.y}" }
                            }
                            onSignatureChanged(serialized)
                            currentLine = emptyList()
                        }
                    )
                }
        ) {
            // Render lines
            for (line in activeLines) {
                if (line.isEmpty()) continue
                val path = Path().apply {
                    val first = line.first()
                    moveTo(first.x * size.width, first.y * size.height)
                    for (i in 1 until line.size) {
                        val pt = line[i]
                        lineTo(pt.x * size.width, pt.y * size.height)
                    }
                }
                drawPath(
                    path = path,
                    color = Color.Blue,
                    style = Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
