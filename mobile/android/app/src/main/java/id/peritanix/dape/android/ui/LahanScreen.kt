package id.peritanix.dape.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.peritanix.dape.physics.Physics

@Composable
fun LahanScreen() {
    val scrollState = rememberScrollState()

    var slopeDeg by remember { mutableStateOf(4.5) }
    var upslopeArea by remember { mutableStateOf(150.0) }
    var sandPct by remember { mutableStateOf(40.0) }
    var siltPct by remember { mutableStateOf(40.0) }
    var clayPct by remember { mutableStateOf(20.0) }

    var hst by remember { mutableStateOf(40) }
    var soilPh by remember { mutableStateOf(5.8) }

    val twi = Physics.twiFromDegrees(upslopeArea, slopeDeg)
    val riskClass = Physics.drainageRiskClass(twi)
    val bedHeight = Physics.requiredBedHeightCm(twi)

    val usdaClass = Physics.classifyUsda(sandPct, siltPct, clayPct)
    val cec = Physics.cecFromTexture(sandPct, siltPct, clayPct)
    val fertPlan = Physics.prescribeFertilizer(soilPh, cec, hst)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Card 1: Hidrologi Mikro & TWI (Hukum Darcy & Topografi) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "1. Hidrologi Mikro & TWI (Topografi Bedengan)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Sensor Klinometer: Rotasi lereng β=${"%.1f".format(slopeDeg)}° | Daerah tangkapan a=${upslopeArea.toInt()} m²/m",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kemiringan Lereng: ${"%.1f".format(slopeDeg)}°")
                }
                Slider(
                    value = slopeDeg.toFloat(),
                    onValueChange = { slopeDeg = it.toDouble() },
                    valueRange = 0.1f..30.0f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daerah Tangkapan (a): ${upslopeArea.toInt()} m²/m")
                }
                Slider(
                    value = upslopeArea.toFloat(),
                    onValueChange = { upslopeArea = it.toDouble() },
                    valueRange = 10f..500f
                )

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Indeks Kebasahan (TWI)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "%.2f".format(twi),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Tinggi Bedengan", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "$bedHeight cm",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (riskClass == "CRITICAL" || riskClass == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(riskClass) }
                    )
                }
                Text(
                    "Rekomendasi: Bedengan minimum $bedHeight cm untuk memutus kapiler air jenuh dan mencegah busuk akar Phytophthora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Card 2: Sedimentasi Stokes & Tekstur Tanah USDA ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "2. Analisis Tekstur Tanah (Hukum Stokes)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Fraksi Pasir (40s): ${sandPct.toInt()}% | Debu (2h): ${siltPct.toInt()}% | Liat: ${clayPct.toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )

                Slider(
                    value = sandPct.toFloat(),
                    onValueChange = {
                        sandPct = it.toDouble()
                        val rem = (100.0 - sandPct).coerceAtLeast(0.0)
                        siltPct = rem * 0.6
                        clayPct = rem * 0.4
                    },
                    valueRange = 0f..100f
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Kelas Tekstur USDA", style = MaterialTheme.typography.bodySmall)
                        Text(
                            usdaClass.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("Kapasitas Tukar Kation (KTK)", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${"%.1f".format(cec)} meq/100g",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- Card 3: Pemupukan Preskriptif Terkalibrasi (Opsi 2) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "3. Dosis Pemupukan Preskriptif (Kocor / L)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Umur Tanaman (HST): $hst HST")
                    Text("pH Tanah: ${"%.1f".format(soilPh)}")
                }
                Slider(
                    value = hst.toFloat(),
                    onValueChange = { hst = it.toInt() },
                    valueRange = 5f..120f
                )
                Slider(
                    value = soilPh.toFloat(),
                    onValueChange = { soilPh = it.toDouble() },
                    valueRange = 4.0f..8.0f
                )

                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column {
                        Text("NPK 16-16-16", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.npk16_16_16GPerL} g/L", fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("KNO3 Kalium", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.kno3GPerL} g/L", fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Kalsium Nitrat", style = MaterialTheme.typography.bodySmall)
                        Text("${fertPlan.calciumGPerL} g/L", fontWeight = FontWeight.Bold)
                    }
                }

                if (fertPlan.dolomiteGPerM2 > 0) {
                    Text(
                        "Advis pH Masam: Aplikasi dolomit ${fertPlan.dolomiteGPerM2} g/m² sebelum pemupukan.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    fertPlan.recommendation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
