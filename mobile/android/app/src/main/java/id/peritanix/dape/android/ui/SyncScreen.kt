package id.peritanix.dape.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.peritanix.dape.android.data.SyncQueueStore

@Composable
fun SyncScreen(queue: SyncQueueStore) {
    var pendingCount by remember { mutableStateOf(queue.pendingCount()) }
    var syncStatus by remember { mutableStateOf("Tersimpan di Room SQLite (Offline-First)") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Status Antrean Sinkronisasi Offline (CRDT)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mutasi Tertunda di SQLite:")
                    Text(
                        "$pendingCount entri",
                        fontWeight = FontWeight.Bold,
                        color = if (pendingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                Text("Data Tersimpan Lokal (Room SQLite):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("• Penilaian Tanah Terdaftar:")
                    Text("${queue.soilAssessmentCount()} entri", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("• Rekam Diagnosis Patologi:")
                    Text("${queue.diagnosisCount()} entri", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("• Batch Panen & Sertifikat QR:")
                    Text("${queue.harvestCount()} entri", fontWeight = FontWeight.SemiBold)
                }

                HorizontalDivider()

                Text(
                    "Status: $syncStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            queue.enqueueSimulatedSync()
                            pendingCount = queue.pendingCount()
                            syncStatus = "Mutasi baru ditambahkan ke antrean offline."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Simulasi Mutasi", style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            queue.schedule()
                            pendingCount = queue.pendingCount()
                            syncStatus = "WorkManager dipicu. Menunggu koneksi stabil untuk flush ke Go API."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Flush Sync", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedButton(
                    onClick = {
                        pendingCount = queue.pendingCount()
                        syncStatus = "Statistik antrean lokal diperbarui."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh Data Lokal", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Prinsip Offline-First DAPE:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("• Seluruh komputasi fisika (Stokes, TWI, Asas Black, Archimedes) berjalan lokal tanpa internet.", style = MaterialTheme.typography.bodySmall)
                Text("• Diagnostik daun & uji ooze menghasilkan resep obat langsung di kebun.", style = MaterialTheme.typography.bodySmall)
                Text("• Idempotency-key menjamin transmisi ulang tidak memicu duplikasi data di PostGIS.", style = MaterialTheme.typography.bodySmall)
                Text("• State-based LWW (Last-Write-Wins) CRDT otomatis menyelesaikan konflik edit lahan multi-operator.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
