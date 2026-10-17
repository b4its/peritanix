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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.sync.P2pFieldMeshSync
import id.peritanix.dape.android.core.sync.SmartCloudSync
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import id.peritanix.dape.android.ui.theme.OrangePrimary
import org.json.JSONObject


@Composable
fun SyncScreen(queue: SyncQueueStore) {
    val context = LocalContext.current
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

                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Mutasi Ditambahkan ke Antrean",
                                message = "1 data mutasi offline baru berhasil diantrekan ke SQLite (Total: $pendingCount pending).",
                                type = NotificationType.SYNC,
                                postSystemNotification = false
                            )
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

                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Sinkronisasi Dipicu",
                                message = "WorkManager menjadwalkan flush antrean delta ($pendingCount item) saat konektivitas aktif.",
                                type = NotificationType.SYNC,
                                postSystemNotification = true
                            )
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

                        PentasixNotificationHub.notify(
                            context = context,
                            title = "Antrean Lokal Diperbarui",
                            message = "Statistik offline: $pendingCount mutasi tersimpan di Room SQLite.",
                            type = NotificationType.INFO,
                            postSystemNotification = false
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh Data Lokal", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Tahap 7: Offline P2P Field Mesh Sync (Data Mule) & Smart Cloud Sync
        val p2pMesh = remember { P2pFieldMeshSync("HP-MANDOR-01") }
        val simulatedPeerQueue = remember { mutableStateListOf<JSONObject>() }
        var meshStatus by remember { mutableStateOf("Jaringan P2P siap (BLE / Wi-Fi Direct).") }

        val cloudSyncStatus = remember(pendingCount) {
            SmartCloudSync.evaluateSyncStrategy(
                pendingJsonRecords = pendingCount,
                pendingMediaItems = if (pendingCount > 0) 3 else 0,
                isOnline = false,
                isWifi = false,
                isCharging = false
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(32.dp).background(Color(0xFF3949AB), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("P2P Field Mesh Sync & Data Mule (Tahap 7)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Sinkronisasi antar-pekerja kebun tanpa kuota & multi-lane cloud", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Jalur Cepat (JSON & Kripto):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${cloudSyncStatus.fastLanePendingCount} rek (${cloudSyncStatus.fastLaneSizeKb} KB)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Jalur Berat (Citra Burst/Video):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${cloudSyncStatus.heavyLanePendingCount} item (${cloudSyncStatus.heavyLaneSizeMb} MB)", fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Kualitas Jaringan:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(cloudSyncStatus.networkQuality.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                        }
                        Text(
                            text = cloudSyncStatus.activeLaneAction,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = meshStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val dummyMutations = listOf(
                                JSONObject().put("id", "DIAG-${System.currentTimeMillis()}").put("action", "DIAGNOSIS_SAVE").put("bed", 3).put("pathogen", "Anthracnose"),
                                JSONObject().put("id", "SOIL-${System.currentTimeMillis()}").put("action", "SOIL_ASSESS").put("ph", 6.2)
                            )
                            val packet = p2pMesh.packLocalMutations(dummyMutations)
                            val merge = p2pMesh.mergePeerPacket(packet, simulatedPeerQueue)
                            meshStatus = merge.statusMessage
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Paket P2P Terkirim ke Data Mule",
                                message = "${merge.mergedCount} mutasi dikemas & digabung via CRDT LWW.",
                                type = NotificationType.SYNC,
                                postSystemNotification = true
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3949AB))
                    ) {
                        Text("Kemas Data Mule P2P", fontSize = 11.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            meshStatus = "Sinkronisasi P2P diperiksa: ${simulatedPeerQueue.size} catatan siap kurir data."
                            PentasixNotificationHub.notify(
                                context = context,
                                title = "Status Mesh P2P",
                                message = "${simulatedPeerQueue.size} catatan tersimpan di antrean kurir data.",
                                type = NotificationType.INFO,
                                postSystemNotification = false
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cek Status Mesh", fontSize = 11.sp, maxLines = 1)
                    }
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
