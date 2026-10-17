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
import androidx.compose.ui.platform.LocalContext
import id.peritanix.dape.android.core.mode.AppMode
import id.peritanix.dape.android.core.mode.AppModeStore
import id.peritanix.dape.android.core.physics.WeatherRepository
import id.peritanix.dape.android.data.SyncQueueStore
import id.peritanix.dape.android.ui.components.AppIcons
import id.peritanix.dape.android.ui.components.ModeBanner
import id.peritanix.dape.android.ui.components.ModeChip
import id.peritanix.dape.android.ui.components.PentasixNotificationBanner
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
    val context = LocalContext.current
    val weatherRepo = remember { WeatherRepository(context.applicationContext) }
    var isDarkTheme by remember { mutableStateOf(false) }

    PentasixTheme(darkTheme = isDarkTheme) {
        var tab by remember { mutableStateOf(0) }
        val modeStore = remember { AppModeStore(context.applicationContext) }
        var mode by remember { mutableStateOf(modeStore.load()) }
        val toggleMode = {
            mode = modeStore.toggle()
        }

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
                        ModeChip(mode = mode, onToggle = toggleMode)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.wrapContentSize().padding(end = 12.dp)
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
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
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
                var menuExpanded by remember { mutableStateOf(false) }
                val isOtherTab = tab >= 2
                val otherTabIcon = when (tab) {
                    2 -> AppIcons.Microscope
                    3 -> AppIcons.Chili
                    4 -> AppIcons.Map
                    5 -> AppIcons.Sync
                    6 -> AppIcons.Compass
                    7 -> AppIcons.WbSunny
                    8 -> AppIcons.Camera
                    9 -> AppIcons.Book
                    else -> AppIcons.MoreVert
                }
                val otherTabLabel = when (tab) {
                    2 -> "Diagnosa ▾"
                    3 -> "Panen ▾"
                    4 -> "Peta ▾"
                    5 -> "Sinkron ▾"
                    6 -> "Kompas ▾"
                    7 -> "Cuaca ▾"
                    8 -> "Kualitas ▾"
                    9 -> "Panduan ▾"
                    else -> "Lainnya ▾"
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    // Item 1: Dasbor
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = {
                            tab = 0
                            menuExpanded = false
                        },
                        icon = {
                            Icon(
                                imageVector = AppIcons.Dashboard,
                                contentDescription = "Dasbor",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                "Dasbor",
                                fontSize = 11.sp,
                                fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Item 2: Lahan
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = {
                            tab = 1
                            menuExpanded = false
                        },
                        icon = {
                            Icon(
                                imageVector = AppIcons.Sprout,
                                contentDescription = "Lahan",
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                "Lahan",
                                fontSize = 11.sp,
                                fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Item 3: Menu Dropdown (Maksimal 3 Menu Bawah)
                    NavigationBarItem(
                        selected = isOtherTab,
                        onClick = { menuExpanded = !menuExpanded },
                        icon = {
                            Box {
                                Icon(
                                    imageVector = otherTabIcon,
                                    contentDescription = otherTabLabel,
                                    modifier = Modifier.size(22.dp)
                                )
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Diagnosa AI Patologi",
                                                fontWeight = if (tab == 2) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.Microscope,
                                                contentDescription = null,
                                                tint = if (tab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (tab == 2) {
                                            { Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            tab = 2
                                            menuExpanded = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Estimasi Panen & Sortasi",
                                                fontWeight = if (tab == 3) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tab == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.Chili,
                                                contentDescription = null,
                                                tint = if (tab == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (tab == 3) {
                                            { Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            tab = 3
                                            menuExpanded = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Peta Spasial GIS",
                                                fontWeight = if (tab == 4) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tab == 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.Map,
                                                contentDescription = null,
                                                tint = if (tab == 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (tab == 4) {
                                            { Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            tab = 4
                                            menuExpanded = false
                                        }
                                    )

                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Sinkronisasi Offline",
                                                fontWeight = if (tab == 5) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tab == 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.Sync,
                                                contentDescription = null,
                                                tint = if (tab == 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (tab == 5) {
                                            { Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            tab = 5
                                            menuExpanded = false
                                        }
                                    )

                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Kompas & Sensor",
                                                fontWeight = if (tab == 6) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tab == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.Compass,
                                                contentDescription = null,
                                                tint = if (tab == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (tab == 6) {
                                            { Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            tab = 6
                                            menuExpanded = false
                                        }
                                    )
                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Cuaca & Hujan Lapangan",
                                                fontWeight = if (tab == 7) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tab == 7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.WbSunny,
                                                contentDescription = null,
                                                tint = if (tab == 7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (tab == 7) {
                                            { Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            tab = 7
                                            menuExpanded = false
                                        }
                                    )

                                    HorizontalDivider()

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Kualitas Cabai (Kamera)",
                                                fontWeight = if (tab == 8) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tab == 8) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.Camera,
                                                contentDescription = null,
                                                tint = if (tab == 8) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (tab == 8) {
                                            { Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            tab = 8
                                            menuExpanded = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Panduan Tanam Cabai",
                                                fontWeight = if (tab == 9) FontWeight.Bold else FontWeight.Normal,
                                                color = if (tab == 9) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = AppIcons.Book,
                                                contentDescription = null,
                                                tint = if (tab == 9) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = if (tab == 9) {
                                            { Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        onClick = {
                                            tab = 9
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                otherTabLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isOtherTab) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
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
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        ModeBanner(mode = mode, onToggle = toggleMode)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                0 -> DashboardScreen(queue, weatherRepo = weatherRepo, onNavigateToTab = { tab = it })
                                1 -> LahanScreen(queue)
                                2 -> DiagnosticsScreen(queue)
                                3 -> HarvestScreen(queue)
                                4 -> FarmMapScreen(queue)
                                5 -> SyncScreen(queue)
                                6 -> CompassCalibrationScreen()
                                7 -> WeatherScreen(weatherRepo)
                                8 -> ChiliQualityScreen()
                                9 -> GrowingGuideScreen()
                                else -> DashboardScreen(queue, weatherRepo = weatherRepo, onNavigateToTab = { tab = it })
                            }
                        }
                    }
                }

                // Global Interactive Action Notification Banner
                PentasixNotificationBanner(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}
