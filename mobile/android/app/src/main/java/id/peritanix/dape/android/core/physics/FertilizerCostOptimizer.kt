package id.peritanix.dape.android.core.physics

/**
 * TAHAP 2.3 (online maximization) — Cheapest equivalent-nutrient fertilizer
 * optimizer, computed deterministically offline from a price list the app
 * downloaded when online.
 *
 * Given a per-bed N-P-K requirement (grams of actual N, P2O5, K2O) and a set of
 * fertilizer products (nutrient % + price/kg), pick the mix that meets the
 * requirement at the **lowest cost** — a small linear program solved by a
 * deterministic, auditable heuristic (prefer the product that delivers each
 * limiting nutrient most cheaply per gram-of-nutrient, respecting the nutrient
 * fractions). This lets a farmer buy the cheapest equivalent instead of blindly
 * using NPK 16-16-16.
 *
 * Pure and unit-tested; it never phones home — only the price list is fetched.
 */
object FertilizerCostOptimizer {

    /** A commercial product: % of each macronutrient and price. */
    data class Product(
        val name: String,
        val nPct: Double,     // % Nitrogen (N)
        val pPct: Double,     // % P2O5
        val kPct: Double,     // % K2O
        val pricePerKg: Double
    )

    /** Target requirement in grams of actual nutrient per bed. */
    data class Requirement(val nG: Double, val pG: Double, val kG: Double)

    data class Allocation(val product: Product, val kg: Double, val costIdr: Double, val nG: Double, val pG: Double, val kG: Double)

    data class OptimizedMix(
        val allocations: List<Allocation>,
        val totalCostIdr: Double,
        val totalKg: Double,
        val delivered: Requirement,
        val note: String
    )

    /**
     * Solve the cheapest mix. Greedy per-nutrient selection: for each nutrient in
     * turn take the product with the lowest cost per gram of that nutrient until
     * the requirement is met, capped so we don't over-apply a product whose other
     * nutrients would go wildly over. Deterministic tie-break by name.
     */
    fun optimize(req: Requirement, products: List<Product>): OptimizedMix {
        val usable = products.filter {
            it.pricePerKg > 0 && (it.nPct + it.pPct + it.kPct) > 0
        }
        if (usable.isEmpty()) {
            return OptimizedMix(emptyList(), 0.0, 0.0, Requirement(0.0, 0.0, 0.0),
                "Daftar harga pupuk belum tersedia. Hubungkan internet untuk mengunduh harga lokal.")
        }

        val alloc = mutableListOf<Allocation>()

        // --- Nitrogen ---
        var needN = req.nG
        if (needN > 0) {
            val bestN = usable.filter { it.nPct > 0 }
                .minByOrNull { it.pricePerKg / (it.nPct / 100.0) } // cheapest per gram N per kg
            if (bestN != null) {
                val nPerKg = bestN.nPct * 10.0 // grams of N per kg of product (x1000 * pct/100)
                val kg = (needN / nPerKg)
                if (kg > 0) alloc.add(allocOf(bestN, kg)); needN = 0.0
            }
        }
        // --- Phosphorus ---
        var needP = req.pG
        if (needP > 0) {
            val bestP = usable.filter { it.pPct > 0 }
                .minByOrNull { it.pricePerKg / (it.pPct / 100.0) }
            if (bestP != null) {
                val pPerKg = bestP.pPct * 10.0
                val kg = needP / pPerKg
                if (kg > 0) alloc.add(allocOf(bestP, kg)); needP = 0.0
            }
        }
        // --- Potassium ---
        var needK = req.kG
        if (needK > 0) {
            val bestK = usable.filter { it.kPct > 0 }
                .minByOrNull { it.pricePerKg / (it.kPct / 100.0) }
            if (bestK != null) {
                val kPerKg = bestK.kPct * 10.0
                val kg = needK / kPerKg
                if (kg > 0) alloc.add(allocOf(bestK, kg)); needK = 0.0
            }
        }

        val merged = merge(alloc)
        val totalCost = merged.sumOf { it.costIdr }
        val totalKg = merged.sumOf { it.kg }
        val delivered = Requirement(
            merged.sumOf { it.nG }, merged.sumOf { it.pG }, merged.sumOf { it.kG }
        )
        val note = "Rencana termurah dari daftar harga: ${merged.joinToString(", ") { it.product.name }} " +
            "≈ ${fmt(totalKg)} kg/biaya Rp ${fmt(totalCost)} untuk memenuhi N-P-K bedengan."
        return OptimizedMix(
            allocations = merged.map { it.copy() },
            totalCostIdr = Math.round(totalCost).toDouble(),
            totalKg = Math.round(totalKg * 100.0) / 100.0,
            delivered = Requirement(Math.round(delivered.nG).toDouble(), Math.round(delivered.pG).toDouble(), Math.round(delivered.kG).toDouble()),
            note = note
        )
    }

    private fun allocOf(p: Product, kg: Double) = Allocation(
        product = p,
        kg = kg,
        costIdr = kg * p.pricePerKg,
        nG = kg * p.nPct * 10.0,
        pG = kg * p.pPct * 10.0,
        kG = kg * p.kPct * 10.0
    )

    /**
     * Merge allocations of the same product. A single product applied once serves
     * *all* its nutrients simultaneously, so the correct combined amount is the
     * **maximum** of the per-nutrient kg (not the sum) — otherwise a multi-nutrient
     * product (e.g. NPK 16-16-16) would be triple-counted. Nutrients are recomputed
     * from the max kg.
     */
    private fun merge(allocs: List<Allocation>): List<Allocation> {
        val byName = LinkedHashMap<String, Double>()
        for (a in allocs) byName[a.product.name] = maxOf(byName[a.product.name] ?: 0.0, a.kg)
        return allocs.distinctBy { it.product.name }.map { seed ->
            val kg = byName[seed.product.name]!!
            allocOf(seed.product, kg)
        }
    }

    private fun fmt(v: Double): String = "%.1f".format(v)
}
