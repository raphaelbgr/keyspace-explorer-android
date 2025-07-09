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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.zIndex
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

@Composable
fun KeyspaceScreen(viewModel: KeyspaceViewModel) {
    val items by viewModel.items.collectAsState()
    val bitLength by viewModel.bitLength.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val scannedCount by viewModel.scannedAddressesCount.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val currentScan by viewModel.currentBackgroundScanCount.collectAsState()
    val totalToScan by viewModel.totalBackgroundToScan.collectAsState()

    var showMatches by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PrivateKeyItem?>(null) }
    var sliderValueDecimal by remember { mutableStateOf(BigDecimal.ZERO) }
    var expandedPage by remember { mutableStateOf(false) }
    var scanOnDrag by remember { mutableStateOf(false) }
    var showScaleDialog by remember { mutableStateOf(false) }
    var showManualScanDialog by remember { mutableStateOf(false) }

    val mathContext = MathContext(100)
    var rangeStartSlider by remember { mutableStateOf(BigDecimal.ZERO) }
    var rangeEndSlider by remember { mutableStateOf(BigDecimal.ONE) }

    val estimatedPage = viewModel.estimatePage(sliderValueDecimal.toFloat())
    val fullPageString = estimatedPage.toString()
    val displayPage = if (!expandedPage && fullPageString.length > 6) fullPageString.take(6) + "..." else fullPageString

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        RangeSliderHeader(rangeStartSlider, rangeEndSlider)
        RangeSlider(
            value = rangeStartSlider.toFloat()..rangeEndSlider.toFloat(),
            onValueChange = { range ->
                rangeStartSlider = range.start.toBigDecimal(mathContext)
                rangeEndSlider = range.endInclusive.toBigDecimal(mathContext)
            },
            onValueChangeFinished = {
                val rangeSize = BitcoinUtils.MAX_KEYSPACE.subtract(BitcoinUtils.MIN_KEYSPACE)
                val start = BitcoinUtils.MIN_KEYSPACE + rangeSize.toBigDecimal().multiply(rangeStartSlider, mathContext).toBigInteger()
                val end = BitcoinUtils.MIN_KEYSPACE + rangeSize.toBigDecimal().multiply(rangeEndSlider, mathContext).toBigInteger()
                viewModel.updateKeyspaceRange(start, end)
            },
            steps = 1000,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Progresso no range: ${(viewModel.calculateRelativeProgress() * 100).format(4)}%", fontSize = 18.sp)
        Text("Altura (bits): $bitLength", fontSize = 16.sp)
        Text("Items por página: ${items.size}", fontSize = 14.sp)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Checkbox(checked = scanOnDrag, onCheckedChange = { scanOnDrag = it })
            Text("Scan on drag", modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                PageIndicator(displayPage, expandedPage, fullPageString) { expandedPage = !expandedPage }
                ScanStatus(currentScan, totalToScan)
            }
            ControlsPanel(
                showScaleDialog = showScaleDialog,
                onScaleClick = { showScaleDialog = true },
                showMatches = showMatches,
                onMatchesClick = { showMatches = true },
                scannedCount = scannedCount,
                onManualScanClick = { showManualScanDialog = true }
            )
        }

        val sliderNormalized = sliderValueDecimal.minus(rangeStartSlider)
            .divide(rangeEndSlider.minus(rangeStartSlider).takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE, mathContext)
            .coerceIn(BigDecimal.ZERO, BigDecimal.ONE)

        Slider(
            value = sliderNormalized.toFloat(),
            onValueChange = {
                val newDecimal = rangeStartSlider + (rangeEndSlider - rangeStartSlider)
                    .multiply(it.toBigDecimal(mathContext), mathContext)
                sliderValueDecimal = newDecimal
                if (scanOnDrag) {
                    viewModel.slideToProgressInRange(sliderNormalized.toFloat())
                }
            },
            onValueChangeFinished = {
                viewModel.jumpToProgressInRange(sliderNormalized.toFloat())
            },
            modifier = Modifier.fillMaxWidth()
        )

        Text(summary, color = if (summary.contains("Nenhum")) Color.LightGray else Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items) { item -> KeyItemCard(item) { selectedItem = item } }
        }

        selectedItem?.let {
            IgnoreHoverWrapper {
                KeyDetailDialog(item = it, existsInDb = it.dbHit) { selectedItem = null }
            }
        }

        if (loading) LoadingView()
        if (showMatches) {
            IgnoreHoverWrapper {
                MatchesDialog(
                    onDismiss = { showMatches = false },
                    onSelect = { selectedItem = it; showMatches = false })
            }
        }
        if (showScaleDialog) {
            IgnoreHoverWrapper {
                ScaleDialog(
                    onApply = { minBits, maxBits ->
                        val minValue = BigInteger.ONE.shiftLeft(minBits - 1)
                        val maxValue = BigInteger.ONE.shiftLeft(maxBits).subtract(BigInteger.ONE)
                        viewModel.updateKeyspaceRange(minValue, maxValue)
                        showScaleDialog = false
                    },
                    onCancel = { showScaleDialog = false }
                )
            }
        }
        if (showManualScanDialog) {
            IgnoreHoverWrapper {
                ManualScanDialog(
                    viewModel = viewModel,
                    onDismiss = { showManualScanDialog = false },
                    onScanRequested = { direction, quantity, repeat ->
                        viewModel.startManualScan(direction, quantity, repeat)
                    }
                )
            }
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
                    Text("Index: ${item.index.toScientificNotation()}", fontWeight = FontWeight.Bold)
                    Text("🔑: ...${item.hex.takeLast(6)}", fontSize = 12.sp)
                }
                Text(
                    dbStatusEmoji,
                    fontSize = 18.sp,
                    color = dbStatusColor,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            // 🔁 Mostrar matched com saldo
            item.matched?.forEach { address ->
                Spacer(modifier = Modifier.height(6.dp))
                Column {
                    Text(
                        text = address.fullAddressWithVariant(),
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    if (address.balanceToken > 0.0) {
                        Text(
                            text = "💰 ${address.balanceTokenFormatted} ${address.token} ($${"%.2f".format(address.balanceUsd)})",
                            fontSize = 13.sp,
                            color = Color(0xFFFFD700)
                        )
                    } else {
                        Text(
                            text = "💰 0 ${address.token}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RangeSliderHeader(start: BigDecimal, end: BigDecimal) {
    Text("Range: ${start.setScale(10, BigDecimal.ROUND_HALF_UP)} - ${end.setScale(10, BigDecimal.ROUND_HALF_UP)}", fontSize = 12.sp)
}

@Composable
fun PageIndicator(displayPage: String, expanded: Boolean, fullText: String, onToggle: () -> Unit) {
    Text("Página: $displayPage", fontSize = 12.sp)
    if (fullText.length > 6) {
        Text(
            text = if (expanded) "[ocultar]" else "[expandir]",
            color = Color.Yellow,
            fontSize = 8.sp,
            modifier = Modifier.padding(start = 8.dp).clickable { onToggle() }
        )
    }
}

@Composable
fun ScanStatus(current: Int, total: Int) {
    if (total > 0) {
        val done = current >= total
        val emoji = if (done) "✅" else "⏳"
        val text = if (done) "Escaneamento concluído: $current endereços escaneados" else "Escaneando em background: $current de $total endereços..."
        Text("$emoji $text", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ControlsPanel(
    showScaleDialog: Boolean,
    onScaleClick: () -> Unit,
    showMatches: Boolean,
    onMatchesClick: () -> Unit,
    scannedCount: Int,
    onManualScanClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp)) {
            OutlinedButton(
                onClick = onScaleClick,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("📏 Escala")
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onMatchesClick,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("✅ Matches")
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onManualScanClick,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text("🔍 Scan Manual")
            }
            Text(scannedCount.toString(), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
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
    val map = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹')
    return this.toString().map { map[it] ?: it }.joinToString("")
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize().background(Color.Transparent).zIndex(4.0f)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Carregando...", color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

fun Double.format(digits: Int): String = "% .${digits}f".format(this)

@Preview(showBackground = true)
@Composable
fun PreviewKeyItemCard() {
    val sampleItem = PrivateKeyItem(
        index = BigInteger("12345678901234567890"),
        hex = "0000000000000000000000000000000000000000000000000000000000000001",
        addresses = listOf(
            CryptoAddress(token = "BTC", variant = "P2PKH", address = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
                balanceToken = 1.0,
                balanceTokenFormatted = "1.00000000",
                balanceUsd = 100000.0
                ),
            CryptoAddress(
                token = "BTC", variant = "P2SH", address = "3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy",
                balanceToken = 2.0,
                balanceTokenFormatted = "2.00000000",
                balanceUsd = 200000.0
            )
        ),
        dbHit = true
    )
    KeyItemCard(item = sampleItem, onClick = {})
}