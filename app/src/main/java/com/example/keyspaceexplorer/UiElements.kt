package com.example.keyspaceexplorer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.BigInteger
import java.math.RoundingMode

@Composable
fun KeyspaceScreen(viewModel: KeyspaceViewModel) {
    val items by viewModel.items.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val bitLength by viewModel.bitLength.collectAsState()
    var selectedItem by remember { mutableStateOf<PrivateKeyItem?>(null) }

    var sliderValue by remember { mutableFloatStateOf(progress.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Progresso: ${(progress * 100).format(4)}%", fontSize = 18.sp)
        Text("Altura (bits): $bitLength", fontSize = 16.sp)

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
            },
            onValueChangeFinished = {
                viewModel.jumpToProgress(sliderValue)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items) { item ->
                KeyItemCard(item) { selectedItem = item }
            }
        }

        LaunchedEffect(items) {
            if (items.size < MainActivity.Instance.batchSize * 3) {
                viewModel.loadNextBatch()
            }
        }

        selectedItem?.let {
            KeyDetailDialog(
                item = it,
                existsInDb = it.dbHit
            ) { selectedItem = null }
        }
    }
}

@Composable
fun KeyItemCard(item: PrivateKeyItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Index: ${item.index.toScientificNotation()}", fontWeight = FontWeight.Bold)
                Text(text = "\uD83D\uDD11:" + "...${item.hex.takeLast(6)}")
            }
        }
    }
}

@Composable
fun KeyDetailDialog(
    item: PrivateKeyItem,
    existsInDb: Boolean?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scope = rememberCoroutineScope()
    var dbStatus by remember { mutableStateOf(if (existsInDb == null)"❓ Não verificado" else if (existsInDb) "✅ Existente" else "❌ Não existente") }

    val batchSize = MainActivity.Instance.batchSize
    val index = item.index
    val pageNumber = index.toBigDecimal().divide(batchSize.toBigDecimal(), 0, RoundingMode.FLOOR).toBigInteger()
    val bitLength = index.bitLength()

    val fullText = buildString {
        appendLine("🔢 Index: $index")
        appendLine("📄 Página: $pageNumber")
        appendLine("📦 Tamanho da Página: $batchSize")
        appendLine("📏 Bits: $bitLength")
        appendLine("🔑 Chave Privada (hex): ${item.hex}")
        appendLine("🗃️ Consulta no DB: $dbStatus")
        item.addresses.forEach {
            appendLine("📬 Address: $it")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔍 Detalhes da Chave") },
        text = {
            Column {
                Text(fullText)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    val clip = ClipData.newPlainText("Key Info", fullText)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(context, "Copiado para a área de transferência", Toast.LENGTH_SHORT).show()
                }) {
                    Text("📋 Copiar")
                }
                TextButton(onClick = {
                    scope.launch {
                        dbStatus = "⏳ Consultando..."
                        val matches = RedisService().checkMatches(item.addresses)
                        dbStatus = if (matches.isNotEmpty()) "✅ Existente" else "❌ Não existente"
                    }
                }) {
                    Text("🔍 Verificar no DB")
                }
            }
        }
    )
}

fun BigInteger.toScientificNotation(): String {
    val digits = this.toString().length
    val base = this.toString().first()
    val exponent = digits - 1
    return "$base × 10${exponent.toSuperscript()}"
}

fun Int.toSuperscript(): String {
    val map = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³',
        '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷',
        '8' to '⁸', '9' to '⁹'
    )
    return this.toString().map { map[it] ?: it }.joinToString("")
}

@Composable
fun ConnectionStatusIndicator(isConnecting: Boolean) {
    if (isConnecting) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Red)
                .padding(8.dp)
        ) {
            Text(
                "🔌 Reconectando ao banco de dados...",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

fun Double.format(digits: Int) = "% .${digits}f".format(this)

@Preview(showBackground = true)
@Composable
fun PreviewKeyItemCard() {
    val sampleItem = PrivateKeyItem(
        index = BigInteger("123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"),
        hex = "0000000000000000000000000000000000000000000000000000000000000001",
        addresses = listOf(
            "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            "1BoatSLRHtKNngkdXEeobR76b53LETtpyT"
        )
    )
    KeyItemCard(sampleItem)
}