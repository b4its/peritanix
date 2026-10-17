package id.peritanix.dape.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.peritanix.dape.android.core.hardware.GpsPolygonTracker
import id.peritanix.dape.android.core.physics.TwiLocalCompute
import kotlin.math.*

/**
 * Bounding box for geographic coordinate ranges.
 */
data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    val centerLat: Double get() = (minLat + maxLat) / 2.0
    val centerLon: Double get() = (minLon + maxLon) / 2.0
    val spanLat: Double get() = (maxLat - minLat).coerceAtLeast(0.00001)
    val spanLon: Double get() = (maxLon - minLon).coerceAtLeast(0.00001)
}

/**
 * High-precision Equirectangular / Local Tangent Plane GIS projection engine
 * designed for offline smallholder precision micro-plots (0.1 - 2.0 Ha).
 */
class MapGeoProjection(
    val centerLat: Double,
    val centerLon: Double,
    val pixelsPerMeter: Float,
    val screenWidthPx: Float,
    val screenHeightPx: Float,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f
) {
    companion object {
        const val EARTH_RADIUS_METERS = 6371000.0

        fun computeBounds(points: List<Pair<Double, Double>>, paddingMeters: Double = 12.0): GeoBounds {
            if (points.isEmpty()) {
                val defaultLat = -7.123456
                val defaultLon = 110.456789
                val delta = 0.0005
                return GeoBounds(defaultLat - delta, defaultLat + delta, defaultLon - delta, defaultLon + delta)
            }
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            for ((lat, lon) in points) {
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
            }

            val latPadDeg = (paddingMeters / EARTH_RADIUS_METERS) * (180.0 / Math.PI)
            val cosLat = cos(Math.toRadians((minLat + maxLat) / 2.0)).coerceAtLeast(0.1)
            val lonPadDeg = latPadDeg / cosLat

            return GeoBounds(
                minLat = minLat - latPadDeg,
                maxLat = maxLat + latPadDeg,
                minLon = minLon - lonPadDeg,
                maxLon = maxLon + lonPadDeg
            )
        }

        fun calculateFitScale(
            bounds: GeoBounds,
            screenWidthPx: Float,
            screenHeightPx: Float,
            paddingPx: Float = 36f
        ): Float {
            val centerLat = bounds.centerLat
            val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.1)
            val widthMeters = Math.toRadians(bounds.maxLon - bounds.minLon) * EARTH_RADIUS_METERS * cosLat
            val heightMeters = Math.toRadians(bounds.maxLat - bounds.minLat) * EARTH_RADIUS_METERS

            val usableWidth = (screenWidthPx - paddingPx * 2).coerceAtLeast(100f)
            val usableHeight = (screenHeightPx - paddingPx * 2).coerceAtLeast(100f)

            val scaleX = (usableWidth / widthMeters.coerceAtLeast(8.0)).toFloat()
            val scaleY = (usableHeight / heightMeters.coerceAtLeast(8.0)).toFloat()
            return min(scaleX, scaleY).coerceIn(0.05f, 60.0f)
        }
    }

    private val cosCenterLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.1)

    /** Converts GPS (latitude, longitude) to screen coordinate (x, y) in pixels. */
    fun toScreen(lat: Double, lon: Double): Offset {
        val dLonRad = Math.toRadians(lon - centerLon)
        val dLatRad = Math.toRadians(lat - centerLat)

        val xMeters = dLonRad * EARTH_RADIUS_METERS * cosCenterLat
        val yMeters = dLatRad * EARTH_RADIUS_METERS

        val screenCenterX = screenWidthPx / 2f
        val screenCenterY = screenHeightPx / 2f

        val x = screenCenterX + (xMeters.toFloat() * pixelsPerMeter) + panOffsetX
        val y = screenCenterY - (yMeters.toFloat() * pixelsPerMeter) + panOffsetY

        return Offset(x, y)
    }

    /** Converts screen coordinate (x, y) in pixels to GPS (latitude, longitude). */
    fun toGeo(screenX: Float, screenY: Float): Pair<Double, Double> {
        val screenCenterX = screenWidthPx / 2f
        val screenCenterY = screenHeightPx / 2f

        val xMeters = (screenX - screenCenterX - panOffsetX) / pixelsPerMeter
        val yMeters = -(screenY - screenCenterY - panOffsetY) / pixelsPerMeter

        val dLonRad = xMeters / (EARTH_RADIUS_METERS * cosCenterLat)
        val dLatRad = yMeters / EARTH_RADIUS_METERS

        val lon = centerLon + Math.toDegrees(dLonRad)
        val lat = centerLat + Math.toDegrees(dLatRad)

        return Pair(lat, lon)
    }

    fun metersFromPixels(px: Float): Double = (px / pixelsPerMeter).toDouble()
    fun pixelsFromMeters(meters: Double): Float = (meters * pixelsPerMeter).toFloat()
}

/**
 * Plant pathology and waypoint marker on the farm GIS map.
 */
data class FarmMapMarker(
    val id: String,
    val lat: Double,
    val lon: Double,
    val title: String,
    val diseaseType: String, // ANTHRACNOSE | BEGOMOVIRUS | RALSTONIA | PHYTOPHTHORA | HEALTHY
    val severityPct: Double,
    val localTwi: Double,
    val protocolCode: String = "",
    val actionNote: String = ""
)

/**
 * Representing tapped elements for the interactive inspection card.
 */
sealed class SelectedMapElement {
    data class MarkerElement(val marker: FarmMapMarker) : SelectedMapElement()
    data class VertexElement(val index: Int, val point: GpsPolygonTracker.GpsPoint) : SelectedMapElement()
    data class TwiCellElement(val cell: TwiLocalCompute.TwiCell, val bedHeight: Int) : SelectedMapElement()
}

/**
 * Rich offline-first interactive farm GIS map in Jetpack Compose.
 */
@Composable
fun InteractiveFarmMap(
    modifier: Modifier = Modifier,
    polygonPoints: List<GpsPolygonTracker.GpsPoint> = emptyList(),
    twiCells: List<List<TwiLocalCompute.TwiCell>> = emptyList(),
    diseaseMarkers: List<FarmMapMarker> = emptyList(),
    currentGps: GpsPolygonTracker.GpsPoint? = null,
    flowBearingDeg: Float = 140f,
    windBearingDeg: Float = 60f,
    windSpeedKmh: Float = 8.5f,
    isInteractive: Boolean = true,
    showControls: Boolean = true,
    showLayerToggles: Boolean = true,
    onMarkerClick: ((FarmMapMarker) -> Unit)? = null
) {
    // --- Layer Visibility States ---
    var showPolygon by remember { mutableStateOf(true) }
    var showTwiGrid by remember { mutableStateOf(true) }
    var showDiseaseMarkers by remember { mutableStateOf(true) }
    var showFlowVectors by remember { mutableStateOf(true) }
    var showGpsDot by remember { mutableStateOf(true) }

    // --- Basemap Tile States ---
    var currentBasemapStyle by remember { mutableStateOf(BasemapStyle.OPEN_STREET_MAP) }
    var tileRefreshTrigger by remember { mutableStateOf(0) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        TileCacheManager.init(context)
    }

    // --- Interactive Viewport States (Pan & Zoom) ---
    var zoomFactor by remember { mutableStateOf(1.0f) }
    var panOffsetX by remember { mutableStateOf(0f) }
    var panOffsetY by remember { mutableStateOf(0f) }

    // --- Inspector Selection State ---
    var selectedElement by remember { mutableStateOf<SelectedMapElement?>(null) }

    // Aggregate all geographic coordinates to calculate auto-fit bounding box
    val allCoords = remember(polygonPoints, diseaseMarkers, currentGps) {
        val pts = mutableListOf<Pair<Double, Double>>()
        polygonPoints.forEach { pts.add(Pair(it.latitude, it.longitude)) }
        diseaseMarkers.forEach { pts.add(Pair(it.lat, it.lon)) }
        currentGps?.let { pts.add(Pair(it.latitude, it.longitude)) }
        pts
    }

    val geoBounds = remember(allCoords) {
        MapGeoProjection.computeBounds(allCoords, paddingMeters = 15.0)
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141914))
            .border(1.dp, Color(0xFF2C392C), RoundedCornerShape(12.dp))
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(200f)
        val screenHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(200f)

        val baseScale = remember(geoBounds, screenWidthPx, screenHeightPx) {
            MapGeoProjection.calculateFitScale(geoBounds, screenWidthPx, screenHeightPx)
        }
        val effectiveScale = baseScale * zoomFactor

        val projection = remember(geoBounds.centerLat, geoBounds.centerLon, effectiveScale, screenWidthPx, screenHeightPx, panOffsetX, panOffsetY) {
            MapGeoProjection(
                centerLat = geoBounds.centerLat,
                centerLon = geoBounds.centerLon,
                pixelsPerMeter = effectiveScale,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY
            )
        }

        // Tap and gesture detector
        val gestureModifier = if (isInteractive) {
            Modifier
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomFactor = (zoomFactor * zoom).coerceIn(0.25f, 8.0f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
                .pointerInput(polygonPoints, diseaseMarkers, twiCells, projection) {
                    detectTapGestures { tapOffset ->
                        // 1. Check disease markers
                        if (showDiseaseMarkers) {
                            val hitMarker = diseaseMarkers.firstOrNull { marker ->
                                val pt = projection.toScreen(marker.lat, marker.lon)
                                (pt - tapOffset).getDistance() <= 32f
                            }
                            if (hitMarker != null) {
                                selectedElement = SelectedMapElement.MarkerElement(hitMarker)
                                onMarkerClick?.invoke(hitMarker)
                                return@detectTapGestures
                            }
                        }

                        // 2. Check polygon vertices
                        if (showPolygon) {
                            val hitVertexIdx = polygonPoints.indexOfFirst { pt ->
                                val screenPt = projection.toScreen(pt.latitude, pt.longitude)
                                (screenPt - tapOffset).getDistance() <= 28f
                            }
                            if (hitVertexIdx >= 0) {
                                selectedElement = SelectedMapElement.VertexElement(hitVertexIdx, polygonPoints[hitVertexIdx])
                                return@detectTapGestures
                            }
                        }

                        // 3. Check TWI cells
                        if (showTwiGrid && twiCells.isNotEmpty()) {
                            val rows = twiCells.size
                            val cols = twiCells[0].size
                            val bounds = geoBounds
                            val stepLat = bounds.spanLat / rows
                            val stepLon = bounds.spanLon / cols

                            for (r in 0 until rows) {
                                for (c in 0 until cols) {
                                    val latTop = bounds.maxLat - r * stepLat
                                    val latBottom = bounds.maxLat - (r + 1) * stepLat
                                    val lonLeft = bounds.minLon + c * stepLon
                                    val lonRight = bounds.minLon + (c + 1) * stepLon

                                    val tl = projection.toScreen(latTop, lonLeft)
                                    val br = projection.toScreen(latBottom, lonRight)

                                    val minX = min(tl.x, br.x)
                                    val maxX = max(tl.x, br.x)
                                    val minY = min(tl.y, br.y)
                                    val maxY = max(tl.y, br.y)

                                    if (tapOffset.x in minX..maxX && tapOffset.y in minY..maxY) {
                                        val cell = twiCells[r][c]
                                        val bedHeight = when (cell.riskClass) {
                                            "CRITICAL" -> 50
                                            "HIGH" -> 40
                                            "MODERATE" -> 30
                                            else -> 20
                                        }
                                        selectedElement = SelectedMapElement.TwiCellElement(cell, bedHeight)
                                        return@detectTapGestures
                                    }
                                }
                            }
                        }

                        // Tap empty space clears selection
                        selectedElement = null
                    }
                }
        } else Modifier

        // --- Pure Vector Map Rendering Canvas ---
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .then(gestureModifier)
        ) {
            clipRect {
                // 0. Render Realistic Geographic Basemap Tiles (Slippy Map Tile System)
                val (nLat, wLon) = projection.toGeo(0f, 0f)
                val (sLat, eLon) = projection.toGeo(screenWidthPx, screenHeightPx)
                val spanLat = abs(nLat - sLat)
                val spanLon = abs(eLon - wLon)
                val tileZoom = SlippyTileMath.calculateOptimalZoom(spanLat, spanLon)

                val minTileX = SlippyTileMath.lonToTileX(min(wLon, eLon), tileZoom)
                val maxTileX = SlippyTileMath.lonToTileX(max(wLon, eLon), tileZoom)
                val minTileY = SlippyTileMath.latToTileY(max(nLat, sLat), tileZoom)
                val maxTileY = SlippyTileMath.latToTileY(min(nLat, sLat), tileZoom)

                val maxTilesPerAxis = 1 shl tileZoom
                val startX = minTileX.coerceIn(0, maxTilesPerAxis - 1)
                val endX = maxTileX.coerceIn(startX, min(startX + 5, maxTilesPerAxis - 1))
                val startY = minTileY.coerceIn(0, maxTilesPerAxis - 1)
                val endY = maxTileY.coerceIn(startY, min(startY + 5, maxTilesPerAxis - 1))

                for (tx in startX..endX) {
                    for (ty in startY..endY) {
                        val tile = SlippyTile(tx, ty, tileZoom)
                        val tBounds = SlippyTileMath.getTileBounds(tx, ty, tileZoom)
                        val tl = projection.toScreen(tBounds.maxLat, tBounds.minLon)
                        val br = projection.toScreen(tBounds.minLat, tBounds.maxLon)
                        val tWidth = br.x - tl.x
                        val tHeight = br.y - tl.y

                        if (tWidth > 0 && tHeight > 0) {
                            val cachedBitmap = TileCacheManager.getTile(tile, currentBasemapStyle)
                            if (cachedBitmap != null) {
                                drawImage(
                                    image = cachedBitmap,
                                    dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                                    dstSize = IntSize(tWidth.toInt(), tHeight.toInt())
                                )
                            } else {
                                TileCacheManager.requestTile(tile, currentBasemapStyle) {
                                    tileRefreshTrigger++
                                }
                                drawRealisticBasemapTile(
                                    tile = tile,
                                    style = currentBasemapStyle,
                                    topLeft = tl,
                                    size = Size(tWidth, tHeight)
                                )
                            }
                        }
                    }
                }

                // 1. Draw subtle background terrain grid lines (every 20 meters)
                drawGeoGrid(projection, screenWidthPx, screenHeightPx)

                // 2. Draw TWI Hydrological Risk Grid Overlay
                if (showTwiGrid && twiCells.isNotEmpty()) {
                    drawTwiRasterGrid(twiCells, geoBounds, projection)
                }

                // 3. Draw Water Runoff Flow & Wind Vector Arrows
                if (showFlowVectors) {
                    drawHydrologyAndWindVectors(geoBounds, projection, flowBearingDeg, windBearingDeg)
                }

                // 4. Draw Farm Polygon Boundary & Vertices
                if (showPolygon && polygonPoints.isNotEmpty()) {
                    drawFarmPolygon(polygonPoints, projection)
                }

                // 5. Draw Disease Pathology Waypoints
                if (showDiseaseMarkers && diseaseMarkers.isNotEmpty()) {
                    drawDiseaseMarkers(diseaseMarkers, projection)
                }

                // 6. Draw Farmer Live GPS Dot & Accuracy Aura
                if (showGpsDot && currentGps != null) {
                    drawLiveGpsIndicator(currentGps, projection)
                }
            }
        }

        // --- North Compass Badge (Top-Right) ---
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .size(32.dp),
            shape = CircleShape,
            color = Color(0xDD182218),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x55FFFFFF)),
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val nPath = Path().apply {
                        moveTo(cx, 1f)
                        lineTo(cx - 3.5f, cy)
                        lineTo(cx + 3.5f, cy)
                        close()
                    }
                    drawPath(nPath, Color(0xFFFF5252))
                    val sPath = Path().apply {
                        moveTo(cx, size.height - 1f)
                        lineTo(cx - 3.5f, cy)
                        lineTo(cx + 3.5f, cy)
                        close()
                    }
                    drawPath(sPath, Color(0xFFE0E0E0))
                }
            }
        }

        // --- Top Controls: Basemap Style Selector + Layer Toggles (Single Horizontal Scroll Row) ---
        var basemapMenuExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp, end = 44.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Basemap Dropdown Chip
            Box {
                val currentIcon = when (currentBasemapStyle) {
                    BasemapStyle.OPEN_STREET_MAP -> AppIcons.Map
                    BasemapStyle.CARTO_VOYAGER -> AppIcons.HomeVillage
                    BasemapStyle.SATELLITE_AERIAL -> AppIcons.Satellite
                    BasemapStyle.TOPOGRAPHIC -> AppIcons.Terrain
                }
                val currentLabel = when (currentBasemapStyle) {
                    BasemapStyle.OPEN_STREET_MAP -> "Peta: OSM"
                    BasemapStyle.CARTO_VOYAGER -> "Peta: Desa"
                    BasemapStyle.SATELLITE_AERIAL -> "Peta: Satelit"
                    BasemapStyle.TOPOGRAPHIC -> "Peta: Topo"
                }

                FilterChip(
                    selected = true,
                    onClick = { basemapMenuExpanded = true },
                    leadingIcon = {
                        Icon(currentIcon, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(2.dp))
                            Icon(AppIcons.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xEE2E7D32),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier.height(30.dp)
                )

                DropdownMenu(
                    expanded = basemapMenuExpanded,
                    onDismissRequest = { basemapMenuExpanded = false },
                    modifier = Modifier.background(Color(0xFF1E281E))
                ) {
                    BasemapStyle.values().forEach { style ->
                        val sIcon = when (style) {
                            BasemapStyle.OPEN_STREET_MAP -> AppIcons.Map
                            BasemapStyle.CARTO_VOYAGER -> AppIcons.HomeVillage
                            BasemapStyle.SATELLITE_AERIAL -> AppIcons.Satellite
                            BasemapStyle.TOPOGRAPHIC -> AppIcons.Terrain
                        }
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    sIcon,
                                    contentDescription = null,
                                    tint = if (currentBasemapStyle == style) Color(0xFF81C784) else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            text = {
                                Text(
                                    style.displayName,
                                    fontSize = 12.sp,
                                    color = if (currentBasemapStyle == style) Color(0xFF81C784) else Color.White,
                                    fontWeight = if (currentBasemapStyle == style) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                currentBasemapStyle = style
                                basemapMenuExpanded = false
                                PentasixNotificationHub.notify(
                                    context = context,
                                    title = "Tipe Peta Berubah",
                                    message = "Basemap dialihkan ke ${style.displayName}.",
                                    type = NotificationType.INFO,
                                    postSystemNotification = false
                                )
                            }
                        )
                    }
                }
            }

            // Layer Filter Toggles (in the same row!)
            if (showLayerToggles) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(Color(0x55FFFFFF))
                )

                FilterChip(
                    selected = showPolygon,
                    onClick = {
                        showPolygon = !showPolygon
                        PentasixNotificationHub.notify(
                            context = context,
                            title = if (showPolygon) "Lapisan Lahan Aktif" else "Lapisan Lahan Nonaktif",
                            message = if (showPolygon) "Garis batas pematang poligon GPS ditampilkan." else "Garis batas poligon disembunyikan.",
                            type = NotificationType.INFO,
                            postSystemNotification = false
                        )
                    },
                    leadingIcon = {
                        Icon(AppIcons.Sprout, contentDescription = null, modifier = Modifier.size(12.dp))
                    },
                    label = { Text("Lahan", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xEE2E7D32),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = Color(0xBB1A221A),
                        labelColor = Color.LightGray
                    ),
                    modifier = Modifier.height(30.dp)
                )

                FilterChip(
                    selected = showTwiGrid,
                    onClick = {
                        showTwiGrid = !showTwiGrid
                        PentasixNotificationHub.notify(
                            context = context,
                            title = if (showTwiGrid) "Lapisan TWI Aktif" else "Lapisan TWI Nonaktif",
                            message = if (showTwiGrid) "Grid hidrologi mikro rawan genangan ditampilkan." else "Grid TWI disembunyikan.",
                            type = NotificationType.INFO,
                            postSystemNotification = false
                        )
                    },
                    leadingIcon = {
                        Icon(AppIcons.WaterDrop, contentDescription = null, modifier = Modifier.size(12.dp))
                    },
                    label = { Text("TWI", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xEE0277BD),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = Color(0xBB1A221A),
                        labelColor = Color.LightGray
                    ),
                    modifier = Modifier.height(30.dp)
                )

                FilterChip(
                    selected = showDiseaseMarkers,
                    onClick = {
                        showDiseaseMarkers = !showDiseaseMarkers
                        PentasixNotificationHub.notify(
                            context = context,
                            title = if (showDiseaseMarkers) "Lapisan Penyakit Aktif" else "Lapisan Penyakit Nonaktif",
                            message = if (showDiseaseMarkers) "Titik koordinat infeksi patologi ditampilkan." else "Titik patologi disembunyikan.",
                            type = NotificationType.INFO,
                            postSystemNotification = false
                        )
                    },
                    leadingIcon = {
                        Icon(AppIcons.BugReport, contentDescription = null, modifier = Modifier.size(12.dp))
                    },
                    label = { Text("Penyakit", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xEEC62828),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = Color(0xBB1A221A),
                        labelColor = Color.LightGray
                    ),
                    modifier = Modifier.height(30.dp)
                )

                FilterChip(
                    selected = showFlowVectors,
                    onClick = {
                        showFlowVectors = !showFlowVectors
                        PentasixNotificationHub.notify(
                            context = context,
                            title = if (showFlowVectors) "Lapisan Vektor Aktif" else "Lapisan Vektor Nonaktif",
                            message = if (showFlowVectors) "Arah aliran limpasan dan angin spora ditampilkan." else "Vektor arah disembunyikan.",
                            type = NotificationType.INFO,
                            postSystemNotification = false
                        )
                    },
                    leadingIcon = {
                        Icon(AppIcons.Wind, contentDescription = null, modifier = Modifier.size(12.dp))
                    },
                    label = { Text("Vektor", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xEEEF6C00),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = Color(0xBB1A221A),
                        labelColor = Color.LightGray
                    ),
                    modifier = Modifier.height(30.dp)
                )

                FilterChip(
                    selected = showGpsDot,
                    onClick = {
                        showGpsDot = !showGpsDot
                        PentasixNotificationHub.notify(
                            context = context,
                            title = if (showGpsDot) "Titik GPS Aktif" else "Titik GPS Nonaktif",
                            message = if (showGpsDot) "Posisi GPS pemantau ditampilkan." else "Posisi GPS disembunyikan.",
                            type = NotificationType.INFO,
                            postSystemNotification = false
                        )
                    },
                    leadingIcon = {
                        Icon(AppIcons.GpsFixed, contentDescription = null, modifier = Modifier.size(12.dp))
                    },
                    label = { Text("GPS", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xEE1565C0),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor = Color(0xBB1A221A),
                        labelColor = Color.LightGray
                    ),
                    modifier = Modifier.height(30.dp)
                )
            }
        }

        val scaleMeters = remember(effectiveScale) {
            val metersRaw = projection.metersFromPixels(60f)
            when {
                metersRaw < 7.5 -> 5
                metersRaw < 15.0 -> 10
                metersRaw < 35.0 -> 20
                metersRaw < 75.0 -> 50
                metersRaw < 150.0 -> 100
                else -> 200
            }
        }

        // Only show bottom overlays when inspector card is not active
        if (selectedElement == null) {
            // --- Offline Status / Basemap Attribution Badge ---
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp),
                color = Color(0xDD121812),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x44FFFFFF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(AppIcons.Shield, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(11.dp))
                    Text(
                        currentBasemapStyle.displayName,
                        fontSize = 9.sp,
                        color = Color(0xFFE0E0E0),
                        fontFamily = FontFamily.Monospace
                    )
                    Text("|", fontSize = 9.sp, color = Color(0x44FFFFFF))
                    Text(
                        "~${scaleMeters}m",
                        fontSize = 9.sp,
                        color = Color(0xFFA5D6A7),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // --- Sleek Vertical Zoom Pill Toolbar (Floating Bottom-Right) ---
            if (showControls) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xDD1A241A),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x55FFFFFF)),
                    shadowElevation = 3.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(32.dp)
                    ) {
                        IconButton(
                            onClick = { zoomFactor = (zoomFactor * 1.35f).coerceAtMost(8.0f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(AppIcons.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        HorizontalDivider(color = Color(0x33FFFFFF), thickness = 0.5.dp)
                        IconButton(
                            onClick = { zoomFactor = (zoomFactor / 1.35f).coerceAtLeast(0.25f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(AppIcons.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        HorizontalDivider(color = Color(0x33FFFFFF), thickness = 0.5.dp)
                        IconButton(
                            onClick = {
                                zoomFactor = 1.0f
                                panOffsetX = 0f
                                panOffsetY = 0f
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(AppIcons.GpsFixed, contentDescription = "Pusatkan", tint = Color(0xFF81C784), modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        // --- Interactive Callout / Inspector Card (Bottom Overlay) ---
        selectedElement?.let { element ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C241C)),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (element) {
                            is SelectedMapElement.MarkerElement -> {
                                val m = element.marker
                                val markerColor = when (m.diseaseType) {
                                    "ANTHRACNOSE" -> Color(0xFFFF5252)
                                    "BEGOMOVIRUS" -> Color(0xFFFFD740)
                                    "RALSTONIA" -> Color(0xFFE040FB)
                                    "PHYTOPHTHORA" -> Color(0xFFFF4081)
                                    else -> Color(0xFF69F0AE)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.BugReport, contentDescription = null, tint = markerColor, modifier = Modifier.size(16.dp))
                                    Text(
                                        m.title,
                                        fontWeight = FontWeight.Bold,
                                        color = markerColor,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                            is SelectedMapElement.VertexElement -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.GpsFixed, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                                    Text(
                                        "Titik Pematang #${element.index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF81C784),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                            is SelectedMapElement.TwiCellElement -> {
                                val c = element.cell
                                val cellRiskColor = when (c.riskClass) {
                                    "CRITICAL" -> Color(0xFFFF5252)
                                    "HIGH" -> Color(0xFFFFAB40)
                                    "MODERATE" -> Color(0xFF69F0AE)
                                    else -> Color(0xFF40C4FF)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(AppIcons.WaterDrop, contentDescription = null, tint = cellRiskColor, modifier = Modifier.size(16.dp))
                                    Text(
                                        "Sel TWI [${c.row}, ${c.col}]: ${c.riskClass}",
                                        fontWeight = FontWeight.Bold,
                                        color = cellRiskColor,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { selectedElement = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(AppIcons.Close, contentDescription = "Tutup", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }

                    when (element) {
                        is SelectedMapElement.MarkerElement -> {
                            val m = element.marker
                            Text("Keparahan: ${m.severityPct.toInt()}% | TWI Mikro: ${m.localTwi}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                            if (m.actionNote.isNotEmpty()) {
                                Text("Aksi: ${m.actionNote}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFE082))
                            }
                            Text(
                                "GPS: ${"%.6f".format(m.lat)}, ${"%.6f".format(m.lon)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                        }
                        is SelectedMapElement.VertexElement -> {
                            val v = element.point
                            Text("Elevasi: ${v.altitudeM.toInt()} m | Akurasi: ±${v.accuracyM} m", style = MaterialTheme.typography.bodySmall, color = Color.White)
                            Text(
                                "GPS: ${"%.6f".format(v.latitude)}, ${"%.6f".format(v.longitude)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                        }
                        is SelectedMapElement.TwiCellElement -> {
                            val c = element.cell
                            Text("Indeks TWI: ${c.twi} | Lereng: ${c.slopeDeg}° | Catchment: ${c.upslopeM2} m²/m", style = MaterialTheme.typography.bodySmall, color = Color.White)
                            Text("Rekomendasi Bedengan: ${element.bedHeight} cm", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFE082), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Canvas Vector Drawing Helpers
// =============================================================================

private fun DrawScope.drawGeoGrid(
    projection: MapGeoProjection,
    widthPx: Float,
    heightPx: Float
) {
    val gridSpacingPx = projection.pixelsFromMeters(20.0).coerceIn(30f, 150f)
    var x = (widthPx / 2f + projection.panOffsetX) % gridSpacingPx
    while (x < widthPx) {
        drawLine(
            color = Color(0x1F4CAF50),
            start = Offset(x, 0f),
            end = Offset(x, heightPx),
            strokeWidth = 1f
        )
        x += gridSpacingPx
    }
    var y = (heightPx / 2f + projection.panOffsetY) % gridSpacingPx
    while (y < heightPx) {
        drawLine(
            color = Color(0x1F4CAF50),
            start = Offset(0f, y),
            end = Offset(widthPx, y),
            strokeWidth = 1f
        )
        y += gridSpacingPx
    }
}

private fun DrawScope.drawTwiRasterGrid(
    grid: List<List<TwiLocalCompute.TwiCell>>,
    bounds: GeoBounds,
    projection: MapGeoProjection
) {
    val rows = grid.size
    val cols = grid[0].size
    val stepLat = bounds.spanLat / rows
    val stepLon = bounds.spanLon / cols

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val cell = grid[r][c]
            val latTop = bounds.maxLat - r * stepLat
            val latBottom = bounds.maxLat - (r + 1) * stepLat
            val lonLeft = bounds.minLon + c * stepLon
            val lonRight = bounds.minLon + (c + 1) * stepLon

            val tl = projection.toScreen(latTop, lonLeft)
            val br = projection.toScreen(latBottom, lonRight)

            val minX = min(tl.x, br.x)
            val maxX = max(tl.x, br.x)
            val minY = min(tl.y, br.y)
            val maxY = max(tl.y, br.y)

            val cellColor = when (cell.riskClass) {
                "CRITICAL" -> Color(0x66D32F2F) // Water pooling depression
                "HIGH" -> Color(0x55F57C00)
                "MODERATE" -> Color(0x44388E3C)
                else -> Color(0x331976D2)
            }

            drawRect(
                color = cellColor,
                topLeft = Offset(minX, minY),
                size = Size(maxX - minX, maxY - minY)
            )

            drawRect(
                color = Color(0x33FFFFFF),
                topLeft = Offset(minX, minY),
                size = Size(maxX - minX, maxY - minY),
                style = Stroke(width = 1f)
            )
        }
    }
}

private fun DrawScope.drawHydrologyAndWindVectors(
    bounds: GeoBounds,
    projection: MapGeoProjection,
    flowBearingDeg: Float,
    windBearingDeg: Float
) {
    val centerScreen = projection.toScreen(bounds.centerLat, bounds.centerLon)

    // Runoff flow vectors (cyan / light blue downslope arrows)
    val flowLen = 45f
    val flowRad = Math.toRadians(flowBearingDeg.toDouble() - 90.0) // 0 deg is North (Up)
    val flowEnd = Offset(
        x = centerScreen.x + (flowLen * cos(flowRad)).toFloat(),
        y = centerScreen.y - (flowLen * sin(flowRad)).toFloat()
    )
    drawVectorArrow(
        start = centerScreen,
        end = flowEnd,
        color = Color(0xFF00E5FF),
        strokeWidth = 3f
    )

    // Wind dispersion vector (amber arrow showing spore/vector drift)
    val windLen = 60f
    val windRad = Math.toRadians(windBearingDeg.toDouble() - 90.0)
    val windStart = Offset(centerScreen.x - 30f, centerScreen.y - 30f)
    val windEnd = Offset(
        x = windStart.x + (windLen * cos(windRad)).toFloat(),
        y = windStart.y - (windLen * sin(windRad)).toFloat()
    )
    drawVectorArrow(
        start = windStart,
        end = windEnd,
        color = Color(0xFFFFD54F),
        strokeWidth = 2.5f
    )
}

private fun DrawScope.drawVectorArrow(
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float
) {
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)

    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val arrowLen = 12.0
    val wingAngle = Math.toRadians(25.0)

    val wing1 = Offset(
        x = (end.x - arrowLen * cos(angle - wingAngle)).toFloat(),
        y = (end.y - arrowLen * sin(angle - wingAngle)).toFloat()
    )
    val wing2 = Offset(
        x = (end.x - arrowLen * cos(angle + wingAngle)).toFloat(),
        y = (end.y - arrowLen * sin(angle + wingAngle)).toFloat()
    )

    drawLine(color = color, start = end, end = wing1, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = end, end = wing2, strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

private fun DrawScope.drawFarmPolygon(
    points: List<GpsPolygonTracker.GpsPoint>,
    projection: MapGeoProjection
) {
    if (points.isEmpty()) return

    val screenPoints = points.map { projection.toScreen(it.latitude, it.longitude) }

    // Boundary Path
    val path = Path().apply {
        moveTo(screenPoints[0].x, screenPoints[0].y)
        for (i in 1 until screenPoints.size) {
            lineTo(screenPoints[i].x, screenPoints[i].y)
        }
        if (screenPoints.size >= 3) {
            close()
        }
    }

    // Interior fill (soft vibrant emerald)
    if (screenPoints.size >= 3) {
        drawPath(path = path, color = Color(0x3800E676), style = Fill)
    }

    // Boundary stroke: White casing + Emerald line for crystal clear visibility on any basemap
    drawPath(path = path, color = Color.White, style = Stroke(width = 6f, cap = StrokeCap.Round))
    drawPath(path = path, color = Color(0xFF00C853), style = Stroke(width = 3.5f, cap = StrokeCap.Round))

    // Vertex markers
    screenPoints.forEachIndexed { idx, pt ->
        // Outer halo
        drawCircle(color = Color.White, radius = 7f, center = pt)
        // Inner dot
        drawCircle(
            color = if (idx == 0) Color(0xFF1B5E20) else Color(0xFF00C853),
            radius = 4.5f,
            center = pt
        )
    }
}

private fun DrawScope.drawDiseaseMarkers(
    markers: List<FarmMapMarker>,
    projection: MapGeoProjection
) {
    markers.forEach { marker ->
        val center = projection.toScreen(marker.lat, marker.lon)

        val baseColor = when (marker.diseaseType) {
            "ANTHRACNOSE" -> Color(0xFFD32F2F)
            "BEGOMOVIRUS" -> Color(0xFFFBC02D)
            "RALSTONIA" -> Color(0xFF7B1FA2)
            "PHYTOPHTHORA" -> Color(0xFFC2185B)
            else -> Color(0xFF388E3C)
        }

        // Severity Aura Ring
        val auraRadius = (10f + (marker.severityPct.toFloat() * 0.2f)).coerceIn(12f, 32f)
        drawCircle(
            color = baseColor.copy(alpha = 0.25f),
            radius = auraRadius,
            center = center
        )

        // Marker Pin Outline & Center
        drawCircle(color = Color.White, radius = 7f, center = center)
        drawCircle(color = baseColor, radius = 5f, center = center)
    }
}

private fun DrawScope.drawLiveGpsIndicator(
    gps: GpsPolygonTracker.GpsPoint,
    projection: MapGeoProjection
) {
    val center = projection.toScreen(gps.latitude, gps.longitude)
    val accuracyRadiusPx = projection.pixelsFromMeters(gps.accuracyM.toDouble()).coerceIn(10f, 60f)

    // Accuracy Circle
    drawCircle(
        color = Color(0x2B2196F3),
        radius = accuracyRadiusPx,
        center = center
    )
    drawCircle(
        color = Color(0x662196F3),
        radius = accuracyRadiusPx,
        center = center,
        style = Stroke(width = 1.5f)
    )

    // Center Blue Live Dot
    drawCircle(color = Color.White, radius = 8f, center = center)
    drawCircle(color = Color(0xFF1976D2), radius = 5.5f, center = center)
}
