package id.peritanix.dape.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.theme.OrangePrimary
import id.peritanix.dape.android.ui.theme.PentasixTheme

/**
 * Pentasix Android entry point.
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
        setContent { PentasixApp(queue) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PentasixApp(queue: SyncQueueStore) {
    var isDarkTheme by remember { mutableStateOf(false) }

    PentasixTheme(darkTheme = isDarkTheme) {
        var tab by remember { mutableStateOf(0) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(OrangePrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Chili,
                                    contentDescription = "Pentasix Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Pentasix",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    "Presisi Mikro-Pertanian Cabai",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Shield,
                                    contentDescription = "Offline Mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Offline-First",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = {
                            Icon(
                                imageVector = AppIcons.Dashboard,
                                contentDescription = "Dasbor",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Dasbor", fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = {
                            Icon(
                                imageVector = AppIcons.Sprout,
                                contentDescription = "Lahan",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Lahan", fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = {
                            Icon(
                                imageVector = AppIcons.Microscope,
                                contentDescription = "Diagnosa",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Diagnosa", fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = {
                            Icon(
                                imageVector = AppIcons.Chili,
                                contentDescription = "Panen",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Panen", fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NavigationBarItem(
                        selected = tab == 4,
                        onClick = { tab = 4 },
                        icon = {
                            Icon(
                                imageVector = AppIcons.Map,
                                contentDescription = "Peta",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Peta", fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    NavigationBarItem(
                        selected = tab == 5,
                        onClick = { tab = 5 },
                        icon = {
                            Icon(
                                imageVector = AppIcons.Sync,
                                contentDescription = "Sinkron",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = { Text("Sinkron", fontSize = 10.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
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
                    0 -> DashboardScreen(queue, onNavigateToTab = { tab = it })
                    1 -> LahanScreen(queue)
                    2 -> DiagnosticsScreen(queue)
                    3 -> HarvestScreen(queue)
                    4 -> FarmMapScreen(queue)
                    else -> SyncScreen(queue)
                }
            }
        }
    }
}
