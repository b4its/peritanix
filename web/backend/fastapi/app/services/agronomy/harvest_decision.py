"""Harvest Decision Engine — fuses the post-harvest physics into one action.

Combines the signals DAPE already computes into a single, defensible
recommendation: *when to sell and at what grade*.

Inputs fused:
  - GDD phase & accumulated thermal time (ripeness trajectory)
  - Archimedes sink ratio (density / dry-matter quality proxy)
  - Automated grading (curvature / red% / defect%)  -> physics grade
  - Defect rate (patek risk)                        -> spoilage urgency
  - Optional market price trend                     -> sell-now vs hold

Output:
  - recommended_action: SELL_NOW | HOLD_14D | PROCESS_OR_SORT | REJECT_LOT
  - best_window_days: how many days the batch stays sellable
  - expected_grade & quality_score (0..1, auditable)
  - rationale: human-readable justification

Everything is a closed-form rule over the physics — no learned weights, so a
buyer or extension officer can verify the logic. The engine is deterministic and
unit-tested.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class HarvestInputs:
    accumulated_gdd: float
    current_phase: str = "FRUITING"
    archimedes_sink_ratio: float = 0.0   # 0..1
    curvature_ratio: float = 1.0         # ~1 straight
    red_pct: float = 0.0                 # 0..100
    defect_pct: float = 0.0              # 0..100
    # Optional market signal: expected price change over the next 14 days as a
    # fraction (e.g. +0.15 = +15 %). 0 = unknown/flat.
    price_trend_14d: float = 0.0


@dataclass
class HarvestDecision:
    recommended_action: str
    best_window_days: int
    expected_grade: str
    quality_score: float
    spoilage_risk: str
    rationale: list[str] = field(default_factory=list)


def _grade(sink_ratio: float, curv: float, red_pct: float, defect_pct: float) -> str:
    """Mirror of agronomy/grading.grade_fruit (defects disqualify first)."""
    if defect_pct >= 15.0:
        return "BS"
    if red_pct >= 90.0 and defect_pct < 3.0 and curv <= 1.12:
        return "A+"
    if red_pct >= 75.0 and defect_pct < 5.0 and curv <= 1.25:
        return "A"
    if red_pct >= 50.0 and defect_pct < 10.0:
        return "B"
    return "BS"


def quality_score(inp: HarvestInputs) -> float:
    """Weighted, bounded quality score in [0,1] (auditable weights)."""
    sink = max(0.0, min(1.0, inp.archimedes_sink_ratio))
    ripe = max(0.0, min(1.0, inp.red_pct / 100.0))
    clean = max(0.0, min(1.0, 1.0 - inp.defect_pct / 100.0))
    straight = max(0.0, min(1.0, 1.0 - (inp.curvature_ratio - 1.0) / 0.5))
    return round(0.35 * sink + 0.30 * ripe + 0.25 * clean + 0.10 * straight, 4)


def _spoilage_risk(defect_pct: float) -> str:
    if defect_pct >= 10.0:
        return "HIGH"
    if defect_pct >= 4.0:
        return "MEDIUM"
    return "LOW"


def decide(inp: HarvestInputs) -> HarvestDecision:
    grade = _grade(inp.archimedes_sink_ratio, inp.curvature_ratio,
                   inp.red_pct, inp.defect_pct)
    q = quality_score(inp)
    risk = _spoilage_risk(inp.defect_pct)
    rationale: list[str] = []

    # Patek / defect urgency dominates: a single infected fruit spoils a crate.
    if inp.defect_pct >= 15.0:
        rationale.append(f"Cacat {inp.defect_pct:.1f}% sangat tinggi (risiko patek masif)")
        return HarvestDecision("PROCESS_OR_SORT", 1, grade, q, risk,
                                rationale + ["Sortir ketat & olah; jangan simpan sebagai Grade A."])

    if grade == "BS" and inp.red_pct < 50.0:
        rationale.append(f"Belum matang (merah {inp.red_pct:.0f}%) — mutu BS")
        # Immature: hold for ripening, bounded by defects.
        return HarvestDecision("HOLD_14D", 14 if risk == "LOW" else 7, grade, q, risk,
                                rationale + ["Tunda panen; matang bertahap (GDD)."])

    # Market timing: hold if a meaningful uptrend and the fruit is stable.
    if inp.price_trend_14d >= 0.12 and risk in ("LOW", "MEDIUM") and grade in ("A+", "A"):
        rationale.append(f"Tren harga +{inp.price_trend_14d*100:.0f}% dalam 14 hari & mutu stabil")
        return HarvestDecision("HOLD_14D", 14, grade, q, risk,
                                rationale + ["Tahan sebentar untuk harga lebih baik, cek harian."])

    # Prime and clean -> sell now at peak.
    if grade == "A+" and risk == "LOW":
        rationale.append("Mutu prima (A+, cacat minimal) — jual di puncak")
        return HarvestDecision("SELL_NOW", 5, grade, q, risk,
                                rationale + ["Kunci kontrak harga sekarang."])

    # Default: good but time-sensitive.
    window = 5 if risk == "MEDIUM" else 10
    rationale.append(f"Mutu {grade}, risiko busuk {risk} — jual dalam {window} hari")
    return HarvestDecision("SELL_NOW", window, grade, q, risk, rationale)


def decision_from_gdd_phase(gdd: float, inp: HarvestInputs) -> HarvestDecision:
    """Adjust the base decision with the GDD phase (thermal ripeness)."""
    d = decide(inp)
    phase_note = f"GDD {gdd:.0f} (fase {inp.current_phase})"
    # Before HARVEST phase, do not sell even if a sample looks red enough.
    if inp.current_phase in ("VEGETATIVE", "FLOWERING") and d.recommended_action == "SELL_NOW":
        return HarvestDecision(
            "HOLD_14D", 14, d.expected_grade, d.quality_score, d.spoilage_risk,
            [phase_note, "Fase termal belum masuk panen — tunda meski sampel memerah."])
    d.rationale = [phase_note] + d.rationale
    return d
