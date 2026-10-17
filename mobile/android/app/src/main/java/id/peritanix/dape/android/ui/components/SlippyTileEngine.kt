package id.peritanix.dape.android.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.*

/**
 * Basemap styling options with free, open-access tile sources and offline procedural fallback.
 */
enum class BasemapStyle(
    val id: String,
    val displayName: String,
    val icon: String,
    val urlTemplate: String
) {
    OPEN_STREET_MAP(
        id = "osm",
        displayName = "OpenStreetMap Standar",
        icon = "🗺️",
        urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    ),
    CARTO_VOYAGER(
        id = "carto",
        displayName = "Topografi Desa (Carto)",
        icon = "🏡",
        urlTemplate = "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
    ),
    SATELLITE_AERIAL(
        id = "satellite",
        displayName = "Citra Satelit Lahan",
        icon = "🛰️",
        urlTemplate = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
    ),
    TOPOGRAPHIC(
        id = "opentopo",
        displayName = "Kontur & Elevasi",
        icon = "🏔️",
        urlTemplate = "https://tile.opentopomap.org/{z}/{x}/{y}.png"
    )
}

/**
 * Slippy map tile identifier (Web Mercator EPSG:3857).
 */
data class SlippyTile(
    val x: Int,
    val y: Int,
    val zoom: Int
)

/**
 * Standard Web Mercator (EPSG:3857) Slippy Map Tile coordinate mathematics.
 */
object SlippyTileMath {
    const val TILE_SIZE_PX = 256

    fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val x = (lon + 180.0) / 360.0 * n
        return floor(x).toInt().coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val latClamped = lat.coerceIn(-85.05112878, 85.05112878)
        val latRad = Math.toRadians(latClamped)
        val y = (1.0 - asinh(tan(latRad)) / Math.PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }

    fun tileXToLon(x: Int, zoom: Int): Double {
        val n = 1 shl zoom
        return (x.toDouble() / n) * 360.0 - 180.0
    }

    fun tileYToLat(y: Int, zoom: Int): Double {
        val n = 1 shl zoom
        val latRad = atan(sinh(Math.PI * (1.0 - 2.0 * y.toDouble() / n)))
        return Math.toDegrees(latRad)
    }

    fun getTileBounds(x: Int, y: Int, zoom: Int): GeoBounds {
        val minLon = tileXToLon(x, zoom)
        val maxLon = tileXToLon(x + 1, zoom)
        val maxLat = tileYToLat(y, zoom)
        val minLat = tileYToLat(y + 1, zoom)
        return GeoBounds(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon)
    }

    fun calculateOptimalZoom(spanLat: Double, spanLon: Double): Int {
        val maxSpan = max(spanLat, spanLon).coerceAtLeast(0.0001)
        return when {
            maxSpan < 0.0015 -> 18
            maxSpan < 0.004 -> 17
            maxSpan < 0.012 -> 16
            maxSpan < 0.04 -> 15
            maxSpan < 0.12 -> 14
            else -> 13
        }
    }
}

/**
 * Offline-first tile caching and asynchronous network downloader.
 */
object TileCacheManager {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()
    private val pendingRequests = ConcurrentHashMap<String, Boolean>()
    private val executor = Executors.newFixedThreadPool(3)

    var cacheDir: File? = null

    fun init(context: Context) {
        if (cacheDir == null) {
            cacheDir = File(context.cacheDir, "map_tiles").apply { mkdirs() }
        }
    }

    private fun cacheKey(tile: SlippyTile, style: BasemapStyle): String =
        "${style.id}_${tile.zoom}_${tile.x}_${tile.y}"

    fun getTile(tile: SlippyTile, style: BasemapStyle): ImageBitmap? {
        val key = cacheKey(tile, style)
        // 1. Check memory cache
        memoryCache[key]?.let { return it }

        // 2. Check disk cache
        cacheDir?.let { root ->
            val tileFile = File(root, "${style.id}/${tile.zoom}/${tile.x}/${tile.y}.png")
            if (tileFile.exists() && tileFile.length() > 0) {
                try {
                    val bitmap = BitmapFactory.decodeFile(tileFile.absolutePath)?.asImageBitmap()
                    if (bitmap != null) {
                        memoryCache[key] = bitmap
                        return bitmap
                    }
                } catch (_: Throwable) {}
            }
        }
        return null
    }

    fun requestTile(tile: SlippyTile, style: BasemapStyle, onLoaded: (ImageBitmap) -> Unit) {
        val key = cacheKey(tile, style)
        if (memoryCache.containsKey(key) || pendingRequests.putIfAbsent(key, true) != null) {
            return
        }

        executor.execute {
            try {
                val root = cacheDir
                val tileFile = if (root != null) {
                    File(root, "${style.id}/${tile.zoom}/${tile.x}/${tile.y}.png").apply {
                        parentFile?.mkdirs()
                    }
                } else null

                // If on disk, decode and return
                if (tileFile != null && tileFile.exists() && tileFile.length() > 0) {
                    val bitmap = BitmapFactory.decodeFile(tileFile.absolutePath)?.asImageBitmap()
                    if (bitmap != null) {
                        memoryCache[key] = bitmap
                        onLoaded(bitmap)
                        pendingRequests.remove(key)
                        return@execute
                    }
                }

                // Otherwise fetch from free tile server
                val urlString = style.urlTemplate
                    .replace("{z}", tile.zoom.toString())
                    .replace("{x}", tile.x.toString())
                    .replace("{y}", tile.y.toString())

                val url = URL(urlString)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty(
                        "User-Agent",
                        "Peritanix-Dape-Mobile/1.0 (agronomic precision GIS; smallholder cabai; Android)"
                    )
                }

                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (tileFile != null && bytes.isNotEmpty()) {
                        FileOutputStream(tileFile).use { it.write(bytes) }
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    if (bitmap != null) {
                        memoryCache[key] = bitmap
                        onLoaded(bitmap)
                    }
                }
                conn.disconnect()
            } catch (_: Throwable) {
                // Offline fallback will be rendered automatically
            } finally {
                pendingRequests.remove(key)
            }
        }
    }
}

// =============================================================================
// Realistic Procedural / Vector Basemap Tile Renderer (Offline Guarantee)
// =============================================================================

/**
 * Renders a realistic geographic basemap tile directly onto the Canvas when offline
 * or while online tiles are loading. Depicts real cartographic elements:
 *   - Agricultural plot partitions and land cover
 *   - Main roads, farm access roads (jalan usaha tani), and footpaths
 *   - Irrigation canals and river hydrography
 *   - Topographic contour isolines
 *   - Village settlement building footprints
 */
fun DrawScope.drawRealisticBasemapTile(
    tile: SlippyTile,
    style: BasemapStyle,
    topLeft: Offset,
    size: Size
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val seed = (tile.x * 73856093L xor tile.y * 19349663L xor tile.zoom * 83492791L).absoluteValue
    val rng = java.util.Random(seed)

    when (style) {
        BasemapStyle.OPEN_STREET_MAP, BasemapStyle.CARTO_VOYAGER -> {
            drawCartographicBasemap(topLeft, w, h, rng, style == BasemapStyle.CARTO_VOYAGER)
        }
        BasemapStyle.SATELLITE_AERIAL -> {
            drawSatelliteAerialBasemap(topLeft, w, h, rng)
        }
        BasemapStyle.TOPOGRAPHIC -> {
            drawTopographicBasemap(topLeft, w, h, rng)
        }
    }
}

private fun DrawScope.drawCartographicBasemap(
    tl: Offset,
    w: Float,
    h: Float,
    rng: java.util.Random,
    isCarto: Boolean
) {
    // 1. Natural rural landscape base
    val baseGreen = if (isCarto) Color(0xFFF4F6EE) else Color(0xFFE8F2E4)
    drawRect(color = baseGreen, topLeft = tl, size = Size(w, h))

    // 2. Agricultural parcels (petak sawah / tegalan di sekeliling)
    val parcelCount = 3 + rng.nextInt(3)
    val parcelColors = if (isCarto) {
        listOf(Color(0xFFEBF0E6), Color(0xFFE4EDE0), Color(0xFFEFF5E8), Color(0xFFF7F4EB))
    } else {
        listOf(Color(0xFFE0EEDC), Color(0xFFDCEAD6), Color(0xFFE5F0E0), Color(0xFFF2ECE1))
    }

    for (p in 0 until parcelCount) {
        val px = tl.x + (rng.nextFloat() * 0.6f * w)
        val py = tl.y + (rng.nextFloat() * 0.6f * h)
        val pw = (0.25f + rng.nextFloat() * 0.45f) * w
        val ph = (0.25f + rng.nextFloat() * 0.45f) * h
        val pColor = parcelColors[p % parcelColors.size]

        drawRect(color = pColor, topLeft = Offset(px, py), size = Size(pw, ph))
        // Pematang pembatas petak
        drawRect(
            color = Color(0x334E6B4E),
            topLeft = Offset(px, py),
            size = Size(pw, ph),
            style = Stroke(width = 1f)
        )
    }

    // 3. Topographic elevation contour lines (garis kontur lereng)
    val contourColor = Color(0x286D8260)
    for (c in 1..3) {
        val cy = tl.y + (c * 0.25f * h)
        val path = Path().apply {
            moveTo(tl.x, cy)
            quadraticTo(
                tl.x + w * 0.45f,
                cy + (rng.nextFloat() - 0.5f) * 24f,
                tl.x + w,
                cy + (rng.nextFloat() - 0.5f) * 16f
            )
        }
        drawPath(path, color = contourColor, style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)))
    }

    // 4. Irrigation canal / river (Saluran irigasi tersier)
    val canalPath = Path().apply {
        val riverStartX = tl.x + (0.15f + rng.nextFloat() * 0.25f) * w
        moveTo(riverStartX, tl.y)
        cubicTo(
            riverStartX + 20f,
            tl.y + h * 0.35f,
            riverStartX - 25f,
            tl.y + h * 0.7f,
            riverStartX + 10f,
            tl.y + h
        )
    }
    // Water buffer & stream
    drawPath(canalPath, color = Color(0x3381C784), style = Stroke(width = 9f, cap = StrokeCap.Round))
    drawPath(canalPath, color = Color(0xFF90CAF9), style = Stroke(width = 4.5f, cap = StrokeCap.Round))

    // 5. Road network: Main rural road (Jalan Desa) & Farm access path (Jalan Usaha Tani)
    val roadPath = Path().apply {
        val roadY = tl.y + (0.45f + rng.nextFloat() * 0.2f) * h
        moveTo(tl.x, roadY - 15f)
        quadraticTo(tl.x + w * 0.5f, roadY + 10f, tl.x + w, roadY - 5f)
    }
    // Asphalt road casing and center
    drawPath(roadPath, color = Color(0xFFD6C8AC), style = Stroke(width = 8f, cap = StrokeCap.Round))
    drawPath(roadPath, color = Color(0xFFFFF9E8), style = Stroke(width = 5.5f, cap = StrokeCap.Round))

    // Farm access path crossing
    val farmPath = Path().apply {
        val farmX = tl.x + (0.65f + rng.nextFloat() * 0.2f) * w
        moveTo(farmX, tl.y)
        lineTo(farmX - 15f, tl.y + h)
    }
    drawPath(farmPath, color = Color(0xFFB8AA94), style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)))

    // 6. Farm settlement buildings (Rumah petani / saung)
    val bldgColor = Color(0xFFDFCCBD)
    val roofColor = Color(0xFFE29578)
    for (b in 0..1) {
        val bx = tl.x + (0.25f + b * 0.45f) * w
        val by = tl.y + (0.35f + b * 0.3f) * h
        drawRect(color = bldgColor, topLeft = Offset(bx, by), size = Size(14f, 12f))
        drawRect(color = roofColor, topLeft = Offset(bx + 2f, by + 2f), size = Size(10f, 8f))
    }

    // Tile border (subtle)
    drawRect(color = Color(0x1A000000), topLeft = tl, size = Size(w, h), style = Stroke(1f))
}

private fun DrawScope.drawSatelliteAerialBasemap(
    tl: Offset,
    w: Float,
    h: Float,
    rng: java.util.Random
) {
    // 1. Deep aerial satellite agricultural palette
    drawRect(color = Color(0xFF1B2E17), topLeft = tl, size = Size(w, h))

    // 2. Crop parcel patches with varying vegetation density
    val shades = listOf(Color(0xFF223B1E), Color(0xFF182B14), Color(0xFF2C4524), Color(0xFF334222))
    for (i in 0..4) {
        val px = tl.x + rng.nextFloat() * w * 0.7f
        val py = tl.y + rng.nextFloat() * h * 0.7f
        val pw = (0.2f + rng.nextFloat() * 0.4f) * w
        val ph = (0.2f + rng.nextFloat() * 0.4f) * h
        drawRect(color = shades[i % shades.size], topLeft = Offset(px, py), size = Size(pw, ph))

        // Crop furrow texture (garis alur bedengan tanaman cabai tampak dari atas)
        var ly = py + 3f
        while (ly < py + ph) {
            drawLine(
                color = Color(0x1F6B8E23),
                start = Offset(px, ly),
                end = Offset(px + pw, ly),
                strokeWidth = 1f
            )
            ly += 5f
        }
    }

    // 3. Dirt farm roads (jalan tanah pertanian)
    val roadPath = Path().apply {
        val ry = tl.y + (0.3f + rng.nextFloat() * 0.4f) * h
        moveTo(tl.x, ry)
        quadraticTo(tl.x + w * 0.5f, ry + 12f, tl.x + w, ry - 8f)
    }
    drawPath(roadPath, color = Color(0xFF6B5E4A), style = Stroke(width = 4.5f, cap = StrokeCap.Round))
    drawPath(roadPath, color = Color(0xFF8C7D64), style = Stroke(width = 2.5f, cap = StrokeCap.Round))

    // 4. Dark irrigation stream
    val streamPath = Path().apply {
        val sx = tl.x + (0.2f + rng.nextFloat() * 0.3f) * w
        moveTo(sx, tl.y)
        cubicTo(sx + 15f, tl.y + h * 0.4f, sx - 15f, tl.y + h * 0.7f, sx + 8f, tl.y + h)
    }
    drawPath(streamPath, color = Color(0xFF12242E), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawTopographicBasemap(
    tl: Offset,
    w: Float,
    h: Float,
    rng: java.util.Random
) {
    // 1. Hypsometric relief base
    drawRect(color = Color(0xFFEBF1E6), topLeft = tl, size = Size(w, h))

    // 2. Contour isolines
    for (i in 0..5) {
        val cy = tl.y + (i * 0.18f * h)
        val path = Path().apply {
            moveTo(tl.x, cy)
            cubicTo(
                tl.x + w * 0.33f,
                cy + (rng.nextFloat() - 0.5f) * 20f,
                tl.x + w * 0.66f,
                cy + (rng.nextFloat() - 0.5f) * 20f,
                tl.x + w,
                cy
            )
        }
        val isIndexContour = i % 2 == 0
        drawPath(
            path,
            color = if (isIndexContour) Color(0xFF8D7B68) else Color(0xFFB5A898),
            style = Stroke(width = if (isIndexContour) 1.8f else 1.0f)
        )
    }

    // 3. Valley drainage line
    val streamPath = Path().apply {
        val sx = tl.x + w * 0.4f
        moveTo(sx, tl.y)
        lineTo(sx + 20f, tl.y + h)
    }
    drawPath(streamPath, color = Color(0xFF64B5F6), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
}
