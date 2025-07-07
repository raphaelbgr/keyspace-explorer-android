package com.example.keyspaceexplorer

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.math.BigInteger
import java.math.RoundingMode

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
    val pageNumber =
        index.toBigDecimal().divide(batchSize.toBigDecimal(), 0, RoundingMode.FLOOR).toBigInteger()
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
                        // Index com botão expandir
                        var expandedIndex by remember { mutableStateOf(false) }
                        val indexStr = index.toString()
                        val shortIndex = indexStr.take(6)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔢 Index: ${if (expandedIndex || indexStr.length <= 6) indexStr else "$shortIndex..."}",
                                modifier = Modifier.weight(1f)
                            )
                            if (indexStr.length > 6) {
                                Text(
                                    text = if (expandedIndex) "[ocultar]" else "[expandir]",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clickable { expandedIndex = !expandedIndex },
                                    color = Color.Yellow,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Página com botão expandir
                        var expandedPage by remember { mutableStateOf(false) }
                        val pageStr = pageNumber.toString()
                        val shortPage = pageStr.take(10)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📄 Página: ${if (expandedPage || pageStr.length <= 10) pageStr else "$shortPage..."}",
                                modifier = Modifier.weight(1f)
                            )
                            if (pageStr.length > 10) {
                                Text(
                                    text = if (expandedPage) "[ocultar]" else "[expandir]",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clickable { expandedPage = !expandedPage },
                                    color = Color.Yellow,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text("📦 Tamanho da Página: $batchSize")
                        Text("📏 Bits: $bitLength")
                        Text("🗃️ Consulta no DB: $dbStatus")

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("🔑 Chave Privada", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    val clip = ClipData.newPlainText("Private Key", item.hex)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast
                                        .makeText(
                                            context,
                                            "Chave privada copiada",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = item.hex,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("📬 Endereços Derivados", fontWeight = FontWeight.SemiBold)
                    }

                    items(item.addresses) { addr ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    val clip = ClipData.newPlainText("Wallet Address", addr.address)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast
                                        .makeText(
                                            context,
                                            "Endereço copiado: ${addr.address}",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                },
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = if (addr.variantPretty()
                                            .isNotEmpty()
                                    ) "[${addr.token}//${addr.variantPretty()}]" else "[${addr.token}]",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = addr.address,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
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
                    Toast.makeText(
                        context,
                        "Copiado para a área de transferência",
                        Toast.LENGTH_SHORT
                    ).show()
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