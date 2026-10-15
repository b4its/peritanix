"""Economic impact model — turns DAPE recommendations into rupiah.

Answers the farmer's real question: *does this pay for itself?* Deterministic
and transparent (every coefficient is documented and overridable), so an
extension officer or cooperative can audit the claim.

Two levers, both grounded in the design doc:
  1. Fertilizer saving from prescriptive dosing (20–30 % typical).
  2. Avoided crop loss from early pathogen detection (patek/virus can wipe
     30–100 % of a plot if caught late).
Plus the yield uplift from not aborting flowers (Ca/B fortification).
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class EconomicsInputs:
    area_ha: float
    # Baseline agronomy (what the farmer does today, without DAPE)
    baseline_yield_t_ha: float = 12.0
    price_per_kg: float = 25000.0          # IDR/kg
    fertilizer_cost_per_ha_idr: float = 8_000_000.0
    # DAPE effect coefficients
    fertilizer_saving_pct: float = 0.25    # 20–30 %
    yield_uplift_pct: float = 0.10         # from Ca/B fortification + timing
    # Disease episode: probability of a significant outbreak per season and the
    # fraction of yield lost if it is caught late (baseline) vs early (DAPE).
    outbreak_probability: float = 0.35
    loss_if_late_pct: float = 0.5
    loss_if_early_pct: float = 0.12


@dataclass
class EconomicsResult:
    area_ha: float
    baseline_revenue_idr: float
    darif_revenue_idr: float
    fertilizer_saving_idr: float
    yield_uplift_idr: float
    avoided_loss_idr: float
    total_benefit_idr: float
    estimated_darif_cost_idr: float
    net_benefit_idr: float
    roi_pct: float
    break_even_fraction: float
    notes: list[str] = field(default_factory=list)


# Rough DAPE operating cost per hectare per season: the app is free/offline;
# the real cost is a mid-range phone amortised + occasional consumables.
DEFAULT_DAPE_COST_PER_HA_IDR = 350_000.0


def analyse(inp: EconomicsInputs) -> EconomicsResult:
    if inp.area_ha <= 0:
        raise ValueError("area_ha must be > 0")
    for pct_name, v in (("fertilizer_saving_pct", inp.fertilizer_saving_pct),
                        ("yield_uplift_pct", inp.yield_uplift_pct),
                        ("outbreak_probability", inp.outbreak_probability),
                        ("loss_if_late_pct", inp.loss_if_late_pct),
                        ("loss_if_early_pct", inp.loss_if_early_pct)):
        if not (0.0 <= v <= 1.0):
            raise ValueError(f"{pct_name} must be in [0,1]")

    baseline_yield_kg = inp.baseline_yield_t_ha * 1000.0 * inp.area_ha
    baseline_revenue = baseline_yield_kg * inp.price_per_kg

    # DAPE revenue = baseline * (1 + uplift)
    darif_revenue = baseline_revenue * (1.0 + inp.yield_uplift_pct)

    # Fertilizer saving (₹ on the cost side).
    fertilizer_saving = (inp.fertilizer_cost_per_ha_idr * inp.area_ha *
                         inp.fertilizer_saving_pct)

    # Yield uplift value.
    yield_uplift = darif_revenue - baseline_revenue

    # Avoided loss: with probability p an outbreak occurs; DAPE reduces the lost
    # fraction from loss_if_late to loss_if_early. Expected value.
    expected_loss_late = inp.outbreak_probability * inp.loss_if_late_pct * baseline_revenue
    expected_loss_early = inp.outbreak_probability * inp.loss_if_early_pct * baseline_revenue
    avoided_loss = expected_loss_late - expected_loss_early

    total_benefit = fertilizer_saving + yield_uplift + avoided_loss
    cost = DEFAULT_DAPE_COST_PER_HA_IDR * inp.area_ha
    net = total_benefit - cost
    roi = (net / cost) * 100.0 if cost > 0 else 0.0
    break_even = cost / total_benefit if total_benefit > 0 else 1.0

    notes: list[str] = []
    if inp.area_ha < 0.2:
        notes.append("Lahan < 0.2 ha: manfaat absolut kecil; pertimbangkan skala koperasi.")
    if inp.outbreak_probability >= 0.5:
        notes.append("Tekanan penyakit tinggi — nilai pencegahan dini paling besar di sini.")
    notes.append("Angka berbasis asumsi agronomi yang dapat dikalibrasi dengan data lapangan.")

    return EconomicsResult(
        area_ha=inp.area_ha,
        baseline_revenue_idr=round(baseline_revenue),
        darif_revenue_idr=round(darif_revenue),
        fertilizer_saving_idr=round(fertilizer_saving),
        yield_uplift_idr=round(yield_uplift),
        avoided_loss_idr=round(avoided_loss),
        total_benefit_idr=round(total_benefit),
        estimated_darif_cost_idr=round(cost),
        net_benefit_idr=round(net),
        roi_pct=round(roi, 1),
        break_even_fraction=round(break_even, 4),
        notes=notes,
    )
