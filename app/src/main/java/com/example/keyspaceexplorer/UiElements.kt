package com.example.keyspaceexplorer

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigInteger

@Composable
fun KeyspaceScreen(viewModel: KeyspaceViewModel) {
    val items by viewModel.items.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val bitLength by viewModel.bitLength.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val scannedCount by viewModel.scannedAddressesCount.collectAsState()

    var showMatches by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PrivateKeyItem?>(null) }
    var sliderValue by remember { mutableFloatStateOf(progress.toFloat()) }
    var expandedPage by remember { mutableStateOf(false) }

    val estimatedPage = viewModel.estimatePage(sliderValue) // você deve implementar isso no ViewModel
    val fullPageString = estimatedPage.toString()
    val displayPage = if (!expandedPage && fullPageString.length > 6) {
        fullPageString.take(6) + "..."
    } else {
        fullPageString
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Progresso: ${(progress * 100).format(4)}%", fontSize = 18.sp)
        Text("Altura (bits): $bitLength", fontSize = 16.sp)
        Text("Items por pagina: ${items.size}", fontSize = 14.sp)

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column() {
                Text("Página: $displayPage", fontSize = 12.sp)
                if (fullPageString.length > 6) {
                    Text(
                        text = if (expandedPage) "[ocultar]" else "[expandir]",
                        color = Color.Yellow,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { expandedPage = !expandedPage },
                        fontSize = 8.sp
                    )
                }
                Text(
                    text = "🔍 Endereços escaneados:",
                    fontSize = 12.sp,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = { showMatches = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text("✅ Matches")
                    }

                    Text(
                        text = scannedCount.toString(),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                viewModel.setLoading(true)
                viewModel.slideToProgress(sliderValue)
            },
            onValueChangeFinished = {
                viewModel.setLoading(false)
                viewModel.jumpToProgress(sliderValue)
            },
            modifier = Modifier.fillMaxWidth()
        )

        val hits = items.count { it.dbHit == true }
        val summary = if (hits == 0) "📊 Match com DB: ❌ Nenhum" else "📊 Match com DB: ✅ $hits encontrados"

        Text(
            text = summary,
            color = if (hits > 0) Color(0xFF4CAF50) else Color.LightGray,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
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

        if (showMatches) {
            MatchesDialog(
                onDismiss = { showMatches = false },
                onSelect = { selectedItem = it; showMatches = false }
            )
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
            val dbStatusEmoji = when (item.dbHit) {
                true -> "✅"
                false -> "❌"
                null -> "❓"
            }

            val dbStatusColor = when (item.dbHit) {
                true -> Color(0xFF4CAF50)
                false -> Color.Gray
                null -> Color.LightGray
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Index: ${item.index.toScientificNotation()}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\uD83D\uDD11: ...${item.hex.takeLast(6)}",
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = dbStatusEmoji,
                    fontSize = 18.sp,
                    color = dbStatusColor,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
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