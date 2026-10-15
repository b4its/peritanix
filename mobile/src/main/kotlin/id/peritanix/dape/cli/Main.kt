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
