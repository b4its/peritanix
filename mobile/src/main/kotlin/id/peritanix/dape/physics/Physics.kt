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

    // --- Prescriptive Fertilizer Calculator (Opsi 2) -------------------------

    data class FertilizerPlan(
        val npk16_16_16GPerL: Double,
        val kno3GPerL: Double,
        val calciumGPerL: Double,
        val dolomiteGPerM2: Double,
        val recommendation: String,
    )

    fun prescribeFertilizer(
        ph: Double,
        cecMeq: Double,
        hst: Int,
        targetYieldTHa: Double = 20.0,
    ): FertilizerPlan {
        val phase = when {
            hst < 35 -> "VEGETATIVE"
            hst < 61 -> "FLOWERING"
            else -> "FRUITING"
        }
        val cecFactor = (15.0 / maxOf(cecMeq, 1.0)).coerceIn(0.6, 1.6)
        val yieldFactor = (targetYieldTHa / 20.0).coerceIn(0.8, 1.5)
        val baseNpk = 3.0 * cecFactor * yieldFactor

        var npk = baseNpk
        var kno3 = 0.0
        var calcium = 0.0
        var dolomite = 0.0
        val notes = mutableListOf<String>()

        if (ph < 5.5) {
            dolomite = 150.0 * (5.8 - ph)
            notes.add("pH ${"%.1f".format(ph)} masam: dolomit ${"%.0f".format(dolomite)} g/m2 2 minggu sebelum pupuk.")
        }
        when (phase) {
            "VEGETATIVE" -> {
                notes.add("Fase vegetatif: prioritaskan N.")
            }
            "FLOWERING" -> {
                npk = baseNpk * 0.6
                calcium = 2.5
                kno3 = 1.0
                notes.add("Pembungaan: wajib Ca+B; HINDARI urea/N tinggi.")
            }
            else -> {
                npk = baseNpk * 0.4
                kno3 = 3.0
                calcium = 2.0
                notes.add("Pembuahan: dominan K (KNO3); Ca jaga ketebalan pericarp.")
            }
        }
        return FertilizerPlan(
            npk16_16_16GPerL = Math.round(npk * 100.0) / 100.0,
            kno3GPerL = Math.round(kno3 * 100.0) / 100.0,
            calciumGPerL = Math.round(calcium * 100.0) / 100.0,
            dolomiteGPerM2 = Math.round(dolomite * 100.0) / 100.0,
            recommendation = notes.joinToString(" "),
        )
    }

    // --- Immersion Gyroscope Anti-Cheat Validation (TAHAP 6.3) ----------------

    data class TraceSample(
        val t: Double,
        val gx: Double,
        val gy: Double,
        val gz: Double,
    )

    data class TraceVerdict(
        val valid: Boolean,
        val durationS: Double,
        val samples: Int,
        val motionVariance: Double,
        val score: Double,
        val reasons: List<String>,
    )

    private fun variance(xs: List<Double>): Double {
        val n = xs.size
        if (n < 2) return 0.0
        val mean = xs.sum() / n
        return xs.sumOf { (it - mean) * (it - mean) } / n
    }

    fun validateImmersionTrace(
        samples: List<TraceSample>,
        targetS: Int = 180,
        durationTolS: Int = 20,
        minMotionVariance: Double = 1e-4,
    ): TraceVerdict {
        val reasons = mutableListOf<String>()
        if (samples.size < 5) {
            return TraceVerdict(
                valid = false,
                durationS = 0.0,
                samples = samples.size,
                motionVariance = 0.0,
                score = 0.0,
                reasons = listOf("trace terlalu pendek (< 5 sampel)"),
            )
        }
        val times = samples.map { it.t }
        val duration = (times.maxOrNull() ?: 0.0) - (times.minOrNull() ?: 0.0)
        if (abs(duration - targetS) > durationTolS) {
            reasons.add("durasi ${"%.0f".format(duration)}s di luar target ${targetS}±${durationTolS}s")
        }
        val speeds = samples.map { Math.sqrt(it.gx * it.gx + it.gy * it.gy + it.gz * it.gz) }
        var varv = variance(speeds)
        val gyro = samples.flatMap { listOf(it.gx, it.gy, it.gz) }
        val isConstant = ((gyro.maxOrNull() ?: 0.0) - (gyro.minOrNull() ?: 0.0)) <= 1e-9
        if (isConstant) {
            reasons.add("sinyal giroskop konstan (indikasi lewatkan timer)")
            varv = 0.0
        }
        if (varv < minMotionVariance) {
            reasons.add("variasi gerak $varv < ambang $minMotionVariance")
        }
        val durScore = maxOf(0.0, 1.0 - abs(duration - targetS) / targetS.toDouble())
        val motionScore = minOf(1.0, varv / (minMotionVariance * 10.0))
        val score = Math.round((0.5 * durScore + 0.5 * motionScore) * 1000.0) / 1000.0
        val valid = reasons.isEmpty() && score >= 0.5
        if (valid) {
            reasons.add("bukti fisik wajar: durasi & gerak sesuai")
        }
        return TraceVerdict(
            valid = valid,
            durationS = Math.round(duration * 100.0) / 100.0,
            samples = samples.size,
            motionVariance = varv,
            score = score,
            reasons = reasons,
        )
    }
}
