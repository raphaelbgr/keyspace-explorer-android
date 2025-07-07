package com.example.keyspaceexplorer

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
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
    var loading by remember { mutableStateOf(false) }

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

        selectedItem?.let {
            KeyDetailDialog(
                item = it,
                existsInDb = it.dbHit
            ) { selectedItem = null }
        }

        if (loading) {
            LoadingView()
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

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun KeyDetailDialog(
    item: PrivateKeyItem,
    existsInDb: Boolean?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scope = rememberCoroutineScope()

    var dbStatus by remember {
        mutableStateOf(
            when (existsInDb) {
                null -> "❓ Não verificado"
                true -> "✅ Existente"
                false -> "❌ Não existente"
            }
        )
    }

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
            appendLine("📬 [${it.token} - ${it.variant}] ${it.address}")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("🔍 Detalhes da Chave", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            BoxWithConstraints(modifier = Modifier.heightIn(min = 300.dp, max = 500.dp)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text("📄 Informações Gerais", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🔢 Index: $index")
                        Text("📄 Página: $pageNumber")
                        Text("📦 Tamanho da Página: $batchSize")
                        Text("📏 Bits: $bitLength")
                        Text("🔑 Chave Privada: ${item.hex}")
                        Text("🗃️ Consulta no DB: $dbStatus")

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("📬 Endereços Derivados", fontWeight = FontWeight.SemiBold)
                    }

                    items(item.addresses) { addr ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = if (addr.variantPretty().isNotEmpty()) "[${addr.token}//${addr.variantPretty()}]" else "[${addr.token}]",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = addr.address,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
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
                    Text("📋 Copiar Tudo")
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
fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Carregando...",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
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

@Preview(showBackground = false)
@Composable
fun PreviewKeyDetailDialog() {
    val item = PrivateKeyItem(
        index = BigInteger.ONE,
        hex = "0000000000000000000000000000000000000000000000000000000000000001",
        addresses = listOf(
            CryptoAddress(
                token = "BTC",
                variant = "P2PKH",
                address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            ),
            CryptoAddress(
                token = "BTC",
                variant = "Bech32",
                address = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kygt080",
            ),
            CryptoAddress(
                token = "ETH",
                variant = "ETH",
                address = "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
            )
        )
    )

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            KeyDetailDialog(
                item = item,
                existsInDb = true,
                onDismiss = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewKeyItemCard() {
    val sampleItem = PrivateKeyItem(
        index = BigInteger("12345678901234567890"),
        hex = "0000000000000000000000000000000000000000000000000000000000000001",
        addresses = listOf(
            CryptoAddress(
                token = "BTC",
                variant = "P2PKH",
                address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            ),
            CryptoAddress(
                token = "BTC",
                variant = "P2SH",
                address = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
            )
        ),
        dbHit = true
    )

    KeyItemCard(item = sampleItem, onClick = {})
}