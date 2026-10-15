package id.peritanix.dape.physics

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.tan

/**
 * DAPE deterministic agro-physics. Pure functions, no I/O.
 *
 * Numerically identical to the FastAPI engine (app/services/physics) and the
 * browser mirror (web/frontend/src/lib/physics.ts). This is the single source
 * of truth that runs on-device for true offline operation.
 */
object Physics {

    const val T_BASE_C = 15.0
    const val TARGET_CALORIMETRY_C = 52.0
    const val BOIL_LAPSE_PER_M = 0.0033

    // --- Topographic Wetness Index ------------------------------------------

    private const val MIN_SLOPE_BETA_RAD = 0.1 * Math.PI / 180.0

    /** TWI = ln(a / tan(beta)); beta in radians. Slope floored at 0.1 degrees. */
    fun twi(upslopeAreaM2PerM: Double, slopeBetaRad: Double): Double {
        require(upslopeAreaM2PerM > 0) { "upslope_area must be > 0" }
        val beta = maxOf(abs(slopeBetaRad), MIN_SLOPE_BETA_RAD)
        return ln(upslopeAreaM2PerM / tan(beta))
    }

    fun twiFromDegrees(upslopeAreaM2PerM: Double, slopeBetaDeg: Double): Double =
        twi(upslopeAreaM2PerM, Math.toRadians(slopeBetaDeg))

    fun drainageRiskClass(twiValue: Double): String = when {
        twiValue >= 10.0 -> "CRITICAL"
        twiValue >= 8.0 -> "HIGH"
        twiValue >= 6.0 -> "MODERATE"
        else -> "LOW"
    }

    fun requiredBedHeightCm(twiValue: Double): Int = when {
        twiValue >= 10.0 -> 50
        twiValue >= 8.0 -> 40
        twiValue >= 6.0 -> 30
        else -> 20
    }

    // --- Black's Law calorimetry --------------------------------------------

    /** Local boiling point of water (C) at elevation (m). */
    fun boilingPoint(elevationM: Double): Double = 100.0 - BOIL_LAPSE_PER_M * elevationM

    data class HotWaterRatio(
        val boilingPointC: Double,
        val targetC: Double,
        val wellTempC: Double,
        val vBoilParts: Double,
        val vWellParts: Double,
        val ratioLabel: String,
    )

    /**
     * Black's Law mixing ratio:
     *   V_boil*(T_boil - T_target) = V_well*(T_target - T_well)
     * Parts normalised so the well-water part equals [scale].
     */
    fun hotWaterRatio(
        elevationM: Double,
        wellTempC: Double,
        targetC: Double = TARGET_CALORIMETRY_C,
        scale: Double = 9.0,
    ): HotWaterRatio {
        val tBoil = boilingPoint(elevationM)
        require(wellTempC < targetC && targetC < tBoil) {
            "Target tak tercapai: butuh T_sumur < target < T_didih " +
                "($wellTempC < $targetC < ${"%.3f".format(tBoil)})"
        }
        val vBoil = scale * (targetC - wellTempC) / (tBoil - targetC)
        return HotWaterRatio(
            boilingPointC = tBoil,
            targetC = targetC,
            wellTempC = wellTempC,
            vBoilParts = vBoil,
            vWellParts = scale,
            ratioLabel = "%.1f gayung didih : %.0f gayung sumur".format(vBoil, scale),
        )
    }

    /** Forward model: temperature of a given mix (for verification). */
    fun mixTemperature(elevationM: Double, vBoil: Double, wellTempC: Double, vWell: Double): Double {
        require(vBoil + vWell > 0) { "volumes must be > 0" }
        val tBoil = boilingPoint(elevationM)
        return (vBoil * tBoil + vWell * wellTempC) / (vBoil + vWell)
    }

    // --- Growing Degree Days -------------------------------------------------

    /** GDD_day = max(0, (Tmax+Tmin)/2 - Tbase). */
    fun gddDay(tMin: Double, tMax: Double, tBase: Double = T_BASE_C): Double {
        require(tMax >= tMin) { "t_max must be >= t_min" }
        return maxOf(0.0, (tMax + tMin) / 2.0 - tBase)
    }

    fun phaseForGdd(
        gddCum: Double,
        floweringStart: Double = 350.0,
        fruitingStart: Double = 700.0,
        harvestStart: Double = 1050.0,
    ): String = when {
        gddCum >= harvestStart -> "HARVEST"
        gddCum >= fruitingStart -> "FRUITING"
        gddCum >= floweringStart -> "FLOWERING"
        else -> "VEGETATIVE"
    }

    fun isFortificationWindow(
        gddCum: Double,
        floweringStart: Double = 350.0,
        fruitingStart: Double = 700.0,
    ): Boolean = gddCum >= floweringStart && gddCum < fruitingStart

    // --- Archimedes density assay -------------------------------------------

    /** Weighted sink ratio in [0,1]; neutrals count half. */
    fun archimedesSinkRatio(sinkCount: Int, neutralCount: Int, floatCount: Int,
                            neutralWeight: Double = 0.5): Double {
        val total = sinkCount + neutralCount + floatCount
        require(total > 0) { "no fruits counted" }
        return (sinkCount + neutralWeight * neutralCount) / total
    }

    fun gradeFromSinkRatio(sinkRatio: Double): String = when {
        sinkRatio >= 0.80 -> "A+"
        sinkRatio >= 0.65 -> "A"
        sinkRatio >= 0.45 -> "B"
        else -> "BS"
    }

    /** Relative density of a brine: 1 + 0.0007 * (g salt per mL) * 100. */
    fun brineRelativeDensity(saltG: Double, waterMl: Double): Double {
        require(waterMl > 0 && saltG >= 0) { "invalid brine inputs" }
        val gPerMl = saltG / waterMl
        return 1.0 + 0.0007 * gPerMl * 100.0
    }

    // --- Stokes sedimentation ------------------------------------------------

    const val G = 9.80665
    const val RHO_PARTICLE = 2650.0
    const val RHO_FLUID = 998.0
    const val MU_FLUID = 1.002e-3

    /** Terminal settling velocity (m/s) for a sphere via Stokes' law. */
    fun stokesVelocity(particleRadiusM: Double): Double {
        require(particleRadiusM > 0) { "particle radius must be > 0" }
        return (2.0 / 9.0) * (RHO_PARTICLE - RHO_FLUID) * G *
            (particleRadiusM * particleRadiusM) / MU_FLUID
    }

    /** Classify (sand, silt, clay) fraction percentages into a USDA texture class. */
    fun classifyUsda(sandPct: Double, siltPct: Double, clayPct: Double): String {
        val total = sandPct + siltPct + clayPct
        require(total > 0) { "texture fractions must sum to > 0" }
        val s = sandPct / total * 100.0
        val si = siltPct / total * 100.0
        val cl = clayPct / total * 100.0
        return when {
            cl >= 40 && si < 40 && s <= 45 -> if (si >= 40) "silty clay" else "clay"
            cl >= 35 && s > 45 -> "sandy clay"
            cl in 27.0..<40.0 && s <= 20 && si < 40 -> "silty clay loam"
            cl in 27.0..<40.0 && s > 20 && s <= 45 -> "clay loam"
            cl in 20.0..<35.0 && si >= 40 && s <= 20 -> "silt loam"
            si >= 50 && cl < 27 -> "silt loam"
            cl in 7.0..<27.0 && si in 28.0..<50.0 && s <= 52 -> "loam"
            si >= 28 && cl < 27 && s > 52 -> "sandy loam"
            s >= 70 && cl < 15 -> if (si < 15) "sand" else "loamy sand"
            s >= 43 && cl < 20 -> "sandy loam"
            else -> "loam"
        }
    }

    private val CEC_BY_CLASS = mapOf(
        "sand" to 3.0, "loamy sand" to 5.0, "sandy loam" to 10.0, "loam" to 15.0,
        "silt loam" to 16.0, "silt" to 20.0, "sandy clay loam" to 18.0,
        "clay loam" to 24.0, "silty clay loam" to 26.0, "sandy clay" to 25.0,
        "silty clay" to 34.0, "clay" to 35.0,
    )

    fun cecFromTexture(sandPct: Double, siltPct: Double, clayPct: Double): Double {
        val cls = classifyUsda(sandPct, siltPct, clayPct)
        val base = CEC_BY_CLASS[cls] ?: 15.0
        val total = sandPct + siltPct + clayPct
        val clayFrac = if (total > 0) clayPct / total else 0.0
        return if (clayFrac > 0) base * (0.6 + 0.4 * clayFrac / 0.4) else base
    }
}
