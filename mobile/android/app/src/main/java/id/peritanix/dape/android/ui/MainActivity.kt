package id.peritanix.dape.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.physics.Physics

/**
 * DAPE Android entry point.
 *
 * The UI is deliberately thin: all *decisions* come from the deterministic core
 * (id.peritanix.dape.physics / .crypto), which is shared with the JVM tests. The
 * Android layer only handles I/O — camera, sensors, GPS, Room, WorkManager.
 */
class MainActivity : ComponentActivity() {
    private lateinit var queue: SyncQueueStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        queue = SyncQueueStore(applicationContext)
        setContent { DapeApp(queue) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DapeApp(queue: SyncQueueStore) {
    MaterialTheme {
        var tab by remember { mutableStateOf(0) }
        var pending by remember { mutableStateOf(queue.pendingCount()) }

        Scaffold(
            topBar = { TopAppBar(title = { Text("DAPE — Precision Micro-Farming") }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Text("🌶️") },
                        label = { Text("Lahan") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Text("🔬") },
                        label = { Text("Diagnosis") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Text("📦") },
                        label = { Text("Panen") }
                    )
                }
            }
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    0 -> LandsScreen()
                    1 -> DiagnosticsScreen()
                    else -> HarvestScreen()
                }
                SyncStatusCard(pending, onSync = {
                    queue.enqueueSimulatedSync()
                    pending = queue.pendingCount()
                })
            }
        }
    }
}

@Composable
private fun LandsScreen() {
    // Offline TWI/bed-height guidance from the shared physics core.
    val examples = listOf(100.0 to 3.0, 250.0 to 1.5, 500.0 to 0.5)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hidrologi Mikro (TWI)", style = MaterialTheme.typography.titleMedium)
            examples.forEach { (area, slopeDeg) ->
                val t = Physics.twiFromDegrees(area, slopeDeg)
                Text(
                    "a=$area β=${slopeDeg}° → TWI ${"%.2f".format(t)} " +
                        "(${Physics.drainageRiskClass(t)}, bed ${Physics.requiredBedHeightCm(t)} cm)"
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen() {
    // Calorimetry preview at the current elevation band.
    val r = Physics.hotWaterRatio(elevationM = 850.0, wellTempC = 26.0)
    val mix = Physics.mixTemperature(850.0, r.vBoilParts, 26.0, r.vWellParts)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sterilisasi 52 °C (Asas Black)", style = MaterialTheme.typography.titleMedium)
            Text("Titik didih lokal: ${"%.2f".format(r.boilingPointC)} °C")
            Text(r.ratioLabel)
            Text("Verifikasi model: ${"%.2f".format(mix)} °C")
        }
    }
}

@Composable
private fun HarvestScreen() {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pasca-Panen", style = MaterialTheme.typography.titleMedium)
            Text("Uji Archimedes: sink ratio menentukan grade A+/A/B/BS.")
            Text("Sertifikat ditandatangani Ed25519 secara offline → QR.")
        }
    }
}

@Composable
private fun SyncStatusCard(pending: Int, onSync: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sinkronisasi offline", style = MaterialTheme.typography.titleMedium)
            Text("Antrian tertunda: $pending")
            Button(onClick = onSync) { Text("Tambah & Flush Delta Sync") }
        }
    }
}
