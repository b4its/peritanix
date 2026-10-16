package id.peritanix.dape.cli

import id.peritanix.dape.crypto.Ed25519OfflineSigner
import id.peritanix.dape.physics.Physics

/**
 * DAPE mobile physics smoke CLI.
 *
 * Prints the deterministic engine outputs so you can eyeball them against the
 * FastAPI responses and the browser PWA. Run: `make mobile-run`.
 */
fun main(args: Array<String>) {
    println("=== DAPE Mobile Physics Smoke Test ===")

    // --- Calorimetry at two elevations ---
    for (elevation in listOf(100.0, 1000.0)) {
        val r = Physics.hotWaterRatio(elevation, 27.0)
        val verify = Physics.mixTemperature(elevation, r.vBoilParts, 27.0, r.vWellParts)
        println(
            "Elevation ${elevation.toInt()} m -> boiling ${"%.2f".format(r.boilingPointC)} C, " +
                "ratio ${r.ratioLabel}, mix reaches ${"%.2f".format(verify)} C",
        )
    }

    // --- TWI ---
    val t = Physics.twiFromDegrees(100.0, 5.0)
    println("TWI(a=100, beta=5deg) = ${"%.3f".format(t)} -> ${Physics.drainageRiskClass(t)}, " +
        "bed ${Physics.requiredBedHeightCm(t)} cm")

    // --- GDD ---
    println("GDD(20,30) = ${Physics.gddDay(20.0, 30.0)} -> ${Physics.phaseForGdd(400.0)}")

    // --- Archimedes ---
    val ratio = Physics.archimedesSinkRatio(16, 3, 1)
    println("Archimedes sink ratio = ${"%.3f".format(ratio)} -> grade ${Physics.gradeFromSinkRatio(ratio)}")

    // --- Stokes / USDA ---
    val soil = Physics.classifyUsda(38.0, 40.0, 22.0)
    println("Soil (38/40/22) = $soil, CEC ${"%.2f".format(Physics.cecFromTexture(38.0, 40.0, 22.0))}")

    // --- Prescriptive Fertilizer ---
    val fert = Physics.prescribeFertilizer(ph = 5.2, cecMeq = 14.0, hst = 45)
    println("Fertilizer(HST=45, pH=5.2) -> NPK ${fert.npk16_16_16GPerL} g/L, Ca ${fert.calciumGPerL} g/L, Dolomite ${fert.dolomiteGPerM2} g/m2")

    // --- On-Device Pathology Differentiation ---
    val thrips = id.peritanix.dape.pathology.PathologyClassifier.differentiateLeafCurl("up", "silvery")
    val mites = id.peritanix.dape.pathology.PathologyClassifier.differentiateLeafCurl("down", "bronze")
    println("Pathology curl UP/silvery -> ${thrips.condition} (${thrips.activeIngredient})")
    println("Pathology curl DOWN/bronze -> ${mites.condition} (${mites.activeIngredient})")

    // --- Destructive Ooze Trigger ---
    val ooze = id.peritanix.dape.pathology.PathologyClassifier.evaluateDestructiveOozeTrigger(true, 70.0)
    println("Ooze trigger (wilting + wet 70%) -> mandatory=${ooze.isMandatory} (${ooze.protocolCode})")

    // --- Immersion Sensor Trace Anti-Cheat ---
    val sampleTrace = (0..59).map { i ->
        Physics.TraceSample((180.0 * i) / 59.0, 0.05 * Math.sin(i.toDouble()), 0.05 * Math.cos(i.toDouble() * 1.3), 0.02 * Math.sin(i.toDouble() * 0.7))
    }
    val traceVerdict = Physics.validateImmersionTrace(sampleTrace)
    println("Immersion trace (180s motion) -> valid=${traceVerdict.valid}, score=${traceVerdict.score}")

    // --- Offline certificate ---
    val kp = Ed25519OfflineSigner.fromSeed(ByteArray(32) { (it + 7).toByte() })
    val cert = Ed25519OfflineSigner.certificate(
        mapOf(
            "land_id" to "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "grade_result" to "A",
            "archimedes_sink_ratio" to 0.82,
            "sterilization_c" to 52.0,
        ),
        kp,
    )
    println("Signed certificate (QR payload):")
    println(cert)
}

