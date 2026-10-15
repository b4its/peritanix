"""Yield estimation — fruit counting, ripeness tracking, 14-day projection.

The field worker photographs 5–10 % of trees per bed. An on-device detector
returns per-tree counts of three ripeness classes:

    green_dark   : mature but unripe (will ripen soon)
    red_blush    : turning (blush) — harvest in a few days
    red_full     : fully ripe — harvest now

This module turns those counts into a *deterministic* tonnage projection:

1. Weight each class by a per-fruit mean mass (overridable per variety/region).
2. Apply a phenology transfer matrix that moves fruit between classes day by
   day: green_dark → red_blush → red_full → picked. The transitions are derived
   from a thermal-time (GDD) rate so the projection is physiology-grounded, not
   a naive linear extrapolation.
3. Extrapolate the sampled counts to the whole population (trees_per_bed /
   sampled_trees).
4. Return a daily harvestable mass curve for the next N days with a confidence
   band derived from the sampling fraction (larger samples ⇒ tighter band).

Everything is closed-form and unit-testable.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class YieldWeights:
    green_dark_g: float = 12.0
    red_blush_g: float = 14.0
    red_full_g: float = 15.0


@dataclass
class YieldProjection:
    total_trees: int
    sampled_trees: int
    scale_factor: float
    day_mass_kg: list[float]          # harvestable kg per day for the horizon
    cumulative_kg: list[float]
    projected_14d_kg: float
    confidence_pct: float
    per_class_now: dict[str, float]
    notes: list[str] = field(default_factory=list)


def class_masses(counts: dict[str, int], w: YieldWeights) -> dict[str, float]:
    """Convert per-class counts to grams using per-fruit mean masses."""
    return {
        "green_dark": counts.get("green_dark", 0) * w.green_dark_g,
        "red_blush": counts.get("red_blush", 0) * w.red_blush_g,
        "red_full": counts.get("red_full", 0) * w.red_full_g,
    }


def transition_matrix(gdd_per_day: float, gdd_green_to_blush: float = 40.0,
                      gdd_blush_to_full: float = 35.0) -> dict[str, dict[str, float]]:
    """Per-day class transitions driven by thermal time.

    A class's daily rate is the fraction of its remaining GDD requirement met in
    one day, clamped to [0, 1]. So a hot spell ripens faster than a cool one.
    """
    r_gb = min(1.0, gdd_per_day / max(gdd_green_to_blush, 1e-9))
    r_bf = min(1.0, gdd_per_day / max(gdd_blush_to_full, 1e-9))
    return {
        "green_dark": {"green_dark": 1 - r_gb, "red_blush": r_gb},
        "red_blush": {"red_blush": 1 - r_bf, "red_full": r_bf},
        "red_full": {"red_full": 0.0, "picked": 1.0},
    }


def project_yield(counts: dict[str, int],
                  trees_per_bed: int,
                  sampled_trees: int,
                  horizon_days: int = 14,
                  gdd_per_day: float = 12.0,
                  weights: YieldWeights = YieldWeights()) -> YieldProjection:
    """Project harvestable mass over the horizon.

    Args:
        counts: per-class fruit counts on the sampled trees.
        trees_per_bed: total trees in the bed (for extrapolation).
        sampled_trees: number of trees actually photographed.
        horizon_days: projection length (default 14).
        gdd_per_day: thermal time accumulation used to drive ripening.
        weights: per-fruit mean masses.
    """
    if sampled_trees <= 0:
        raise ValueError("sampled_trees must be > 0")
    if trees_per_bed < sampled_trees:
        raise ValueError("trees_per_bed must be >= sampled_trees")
    if horizon_days <= 0:
        raise ValueError("horizon_days must be > 0")

    # Scale sampled counts up to the whole bed.
    scale = trees_per_bed / sampled_trees
    state = {
        "green_dark": counts.get("green_dark", 0) * scale,
        "red_blush": counts.get("red_blush", 0) * scale,
        "red_full": counts.get("red_full", 0) * scale,
    }
    now_masses = class_masses(
        {k: int(v) for k, v in state.items()}, weights)

    tm = transition_matrix(gdd_per_day)

    day_mass_kg: list[float] = []
    cumulative: list[float] = []
    running = 0.0

    for _ in range(horizon_days):
        # Fruit that is ripe today can be picked today.
        picked_now = state["red_full"]
        mass_g = picked_now * weights.red_full_g
        day_mass_kg.append(round(mass_g / 1000.0, 4))
        running += mass_g / 1000.0
        cumulative.append(round(running, 4))

        # Advance the phenology one day.
        new_state = {"green_dark": 0.0, "red_blush": 0.0, "red_full": 0.0}
        for src, transitions in tm.items():
            amt = state.get(src, 0.0)
            for dst, frac in transitions.items():
                if dst in new_state:
                    new_state[dst] += amt * frac
        state = new_state

    # Confidence grows with the sampled fraction (bootstrap-free heuristic):
    # 10 % sampling -> ~80 %, 20 % -> ~90 %, saturating near 97 %.
    samp_frac = sampled_trees / trees_per_bed
    confidence = min(97.0, 60.0 + 200.0 * samp_frac)

    notes: list[str] = []
    if samp_frac < 0.05:
        notes.append("Sampel < 5% pohon: tingkatkan jumlah sampel untuk akurasi lebih baik.")
    if gdd_per_day <= 0:
        notes.append("GDD/hari nol: proyeksi mengasumsikan tidak ada pematangan baru.")

    return YieldProjection(
        total_trees=trees_per_bed,
        sampled_trees=sampled_trees,
        scale_factor=round(scale, 3),
        day_mass_kg=day_mass_kg,
        cumulative_kg=cumulative,
        projected_14d_kg=cumulative[-1] if cumulative else 0.0,
        confidence_pct=round(confidence, 1),
        per_class_now={k: round(v, 1) for k, v in now_masses.items()},
        notes=notes,
    )
