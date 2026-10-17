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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
            .background(Color(0xFF141914), RoundedCornerShape(12.dp))
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
                .then(gestureModifier)
        ) {
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

            // 7. Draw Map Scale Bar
            drawScaleBar(projection, screenWidthPx, screenHeightPx)

            // 8. Draw Compass North Arrow
            drawNorthCompass(screenWidthPx)
        }

        // --- Layer Filter Chips (Top-Left Horizontal Scroll) ---
        if (showLayerToggles) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = showPolygon,
                    onClick = { showPolygon = !showPolygon },
                    label = { Text("🌾 Lahan", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2E7D32),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = showTwiGrid,
                    onClick = { showTwiGrid = !showTwiGrid },
                    label = { Text("💧 TWI", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0277BD),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = showDiseaseMarkers,
                    onClick = { showDiseaseMarkers = !showDiseaseMarkers },
                    label = { Text("🔬 Penyakit", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFC62828),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = showFlowVectors,
                    onClick = { showFlowVectors = !showFlowVectors },
                    label = { Text("💨 Vektor", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF6C00),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = showGpsDot,
                    onClick = { showGpsDot = !showGpsDot },
                    label = { Text("📍 GPS", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1565C0),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        // --- Zoom & Navigation Controls (Floating Bottom-Right) ---
        if (showControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (selectedElement != null) 140.dp else 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { zoomFactor = (zoomFactor * 1.35f).coerceAtMost(8.0f) },
                    containerColor = Color(0xFF233023),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                SmallFloatingActionButton(
                    onClick = { zoomFactor = (zoomFactor / 1.35f).coerceAtLeast(0.25f) },
                    containerColor = Color(0xFF233023),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Text("−", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                SmallFloatingActionButton(
                    onClick = {
                        zoomFactor = 1.0f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    },
                    containerColor = Color(0xFF233023),
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Text("⛶", fontSize = 14.sp)
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
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (element) {
                            is SelectedMapElement.MarkerElement -> {
                                val m = element.marker
                                Text(
                                    "🔬 ${m.title}",
                                    fontWeight = FontWeight.Bold,
                                    color = when (m.diseaseType) {
                                        "ANTHRACNOSE" -> Color(0xFFFF5252)
                                        "BEGOMOVIRUS" -> Color(0xFFFFD740)
                                        "RALSTONIA" -> Color(0xFFE040FB)
                                        "PHYTOPHTHORA" -> Color(0xFFFF4081)
                                        else -> Color(0xFF69F0AE)
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            is SelectedMapElement.VertexElement -> {
                                Text(
                                    "📍 Titik Pematang #${element.index + 1}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF81C784),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            is SelectedMapElement.TwiCellElement -> {
                                val c = element.cell
                                Text(
                                    "💧 Sel TWI [${c.row}, ${c.col}]: ${c.riskClass}",
                                    fontWeight = FontWeight.Bold,
                                    color = when (c.riskClass) {
                                        "CRITICAL" -> Color(0xFFFF5252)
                                        "HIGH" -> Color(0xFFFFAB40)
                                        "MODERATE" -> Color(0xFF69F0AE)
                                        else -> Color(0xFF40C4FF)
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }

                        IconButton(
                            onClick = { selectedElement = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", color = Color.LightGray, fontSize = 14.sp)
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

    // Interior fill
    if (screenPoints.size >= 3) {
        drawPath(path = path, color = Color(0x384CAF50), style = Fill)
    }

    // Boundary stroke
    drawPath(path = path, color = Color(0xFF66BB6A), style = Stroke(width = 3.5f, cap = StrokeCap.Round))

    // Vertex markers
    screenPoints.forEachIndexed { idx, pt ->
        // Outer halo
        drawCircle(color = Color.White, radius = 6.5f, center = pt)
        // Inner dot
        drawCircle(
            color = if (idx == 0) Color(0xFF2E7D32) else Color(0xFF388E3C),
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

private fun DrawScope.drawScaleBar(
    projection: MapGeoProjection,
    widthPx: Float,
    heightPx: Float
) {
    // Pick standard metric length
    val targetPx = 80f
    val metersRaw = projection.metersFromPixels(targetPx)
    val standardMeters = when {
        metersRaw < 7.5 -> 5
        metersRaw < 15.0 -> 10
        metersRaw < 35.0 -> 20
        metersRaw < 75.0 -> 50
        metersRaw < 150.0 -> 100
        else -> 200
    }
    val barPx = projection.pixelsFromMeters(standardMeters.toDouble())

    val startX = 16f
    val baseY = heightPx - 16f

    // Line with edge ticks
    drawLine(
        color = Color.White,
        start = Offset(startX, baseY),
        end = Offset(startX + barPx, baseY),
        strokeWidth = 2.5f
    )
    drawLine(
        color = Color.White,
        start = Offset(startX, baseY - 5f),
        end = Offset(startX, baseY + 5f),
        strokeWidth = 2.5f
    )
    drawLine(
        color = Color.White,
        start = Offset(startX + barPx, baseY - 5f),
        end = Offset(startX + barPx, baseY + 5f),
        strokeWidth = 2.5f
    )
}

private fun DrawScope.drawNorthCompass(widthPx: Float) {
    val cx = widthPx - 26f
    val cy = 26f

    drawCircle(color = Color(0xAA1C241C), radius = 16f, center = Offset(cx, cy))
    drawCircle(color = Color(0x66FFFFFF), radius = 16f, center = Offset(cx, cy), style = Stroke(1.5f))

    // North Needle (Red pointing Up)
    val needlePath = Path().apply {
        moveTo(cx, cy - 12f)
        lineTo(cx - 4f, cy + 2f)
        lineTo(cx + 4f, cy + 2f)
        close()
    }
    drawPath(needlePath, color = Color(0xFFFF5252))

    // South Needle (White pointing Down)
    val southPath = Path().apply {
        moveTo(cx, cy + 12f)
        lineTo(cx - 4f, cy + 2f)
        lineTo(cx + 4f, cy + 2f)
        close()
    }
    drawPath(southPath, color = Color.White)
}
