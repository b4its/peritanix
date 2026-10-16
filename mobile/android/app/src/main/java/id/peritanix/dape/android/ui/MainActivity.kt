package id.peritanix.dape.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.peritanix.dape.android.data.SyncQueueStore

/**
 * DAPE Android entry point.
 *
 * All deterministic agronomic physics & pathology decisions come from the
 * shared Kotlin core (`id.peritanix.dape.physics` / `id.peritanix.dape.pathology` / `id.peritanix.dape.crypto`),
 * while this Android layer provides the Jetpack Compose user interface, sensor integration,
 * Room SQLite storage, and background synchronization via WorkManager.
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

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("DAPE — Precision Micro-Farming") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Text("🌱") },
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
                        icon = { Text("🌶️") },
                        label = { Text("Benih/Panen") }
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Text("🔄") },
                        label = { Text("Sinkron") }
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                when (tab) {
                    0 -> LahanScreen()
                    1 -> DiagnosticsScreen()
                    2 -> HarvestScreen()
                    else -> SyncScreen(queue)
                }
            }
        }
    }
}
