package com.example.keyspaceexplorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ManualScanDialog(
    viewModel: KeyspaceViewModel,
    onDismiss: () -> Unit,
    onScanRequested: (direction: ScanDirection, quantity: Int, repeatRandom: Boolean) -> Unit
) {
    var quantityText by remember { mutableStateOf("100") }
    var repeatRandom by remember { mutableStateOf(false) }
    val currentStart by viewModel.currentIndex.collectAsState()


    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Text("🔍 Escaneamento Manual\nInício: ${currentStart.toScientificNotation()}")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(text = "Quantidade de chaves") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = repeatRandom, onCheckedChange = { repeatRandom = it })
                    Text("Repetir scan em outro ponto aleatório")
                }

                val progressState by viewModel.manualScanProgress.collectAsState()
                ScanProgressBar(current = progressState.first, total = progressState.second)

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = {
                        val qty = quantityText.toIntOrNull() ?: 0
                        if (qty > 0) onScanRequested(ScanDirection.BACKWARD, qty, repeatRandom)
                    }) {
                        Text("⬅️ Para trás")
                    }
                    Button(onClick = {
                        val qty = quantityText.toIntOrNull() ?: 0
                        if (qty > 0) onScanRequested(ScanDirection.FORWARD, qty, repeatRandom)
                    }) {
                        Text("➡️ Para frente")
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = {
                        val qty = quantityText.toIntOrNull() ?: 0
                        if (qty > 0) onScanRequested(ScanDirection.BOTH, qty, repeatRandom)
                    }) {
                        Text("🔁 Ambos")
                    }
                    TextButton(onClick = {
                        viewModel.cancelManualScan()
                    }) {
                        Text("❌ Cancelar Scan")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Esconder")
            }
        })
}

@Composable
fun ScanProgressBar(current: Int, total: Int) {
    if (total > 0) {
        val progress = current.toFloat() / total
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.small)
            )
            Text(
                text = "Progresso: ${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
            )
        }
    }
}
enum class ScanDirection { FORWARD, BACKWARD, BOTH }