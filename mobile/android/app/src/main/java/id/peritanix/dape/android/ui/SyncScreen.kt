package id.peritanix.dape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons

@Composable
fun SyncScreen(queue: SyncQueueStore) {
    val scrollState = rememberScrollState()
    var pendingCount by remember { mutableStateOf(queue.pendingCount()) }
    var syncStatus by remember { mutableStateOf("Tersimpan di Room SQLite (Offline-First)") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            "Status Antrean Sinkronisasi (CRDT)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Protokol sinkronisasi delta offline-first ke Go Fiber API backend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Mutasi Tertunda di SQLite:",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (pendingCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text(
                                "$pendingCount entri",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                color = if (pendingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text("Data Tersimpan Lokal (Room SQLite):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(AppIcons.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Batas Lahan Poligon GPS:", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${queue.landCount()} entri", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(AppIcons.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Penilaian Tanah Terdaftar:", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${queue.soilAssessmentCount()} entri", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(AppIcons.Microscope, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Rekam Diagnosis Patologi:", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${queue.diagnosisCount()} entri", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(AppIcons.Chili, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Batch Panen & Sertifikat QR:", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${queue.harvestCount()} entri", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Text(
                        "Status: $syncStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            queue.enqueueSimulatedSync()
                            pendingCount = queue.pendingCount()
                            syncStatus = "Mutasi baru ditambahkan ke antrean offline."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
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
                        Icon(AppIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
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
                    Icon(AppIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh Data Lokal", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("Prinsip Offline-First Pentasix:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text("• Seluruh komputasi fisika (Stokes, TWI, Asas Black, Archimedes) berjalan lokal tanpa internet.", style = MaterialTheme.typography.bodySmall)
                Text("• Diagnostik daun & uji ooze menghasilkan resep obat langsung di kebun.", style = MaterialTheme.typography.bodySmall)
                Text("• Idempotency-key menjamin transmisi ulang tidak memicu duplikasi data di PostGIS.", style = MaterialTheme.typography.bodySmall)
                Text("• State-based LWW (Last-Write-Wins) CRDT otomatis menyelesaikan konflik edit lahan multi-operator.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
