"""Archimedes density assay — deterministic fruit-quality physics.

Chilli fruit density correlates with dry-matter and pericarp thickness (both
quality indicators). We float N fruits in a calibrated brine solution
(known relative density rho_sol) and, from a side-view photo, measure the
fraction that sink vs float/neutral.

Fruit relative density:
    sink       -> rho_fruit > rho_sol
    neutral    -> rho_fruit ~= rho_sol
    float      -> rho_fruit < rho_sol

sink_ratio is a robust proxy for high dry-matter / thick pericarp (grade A+).
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class BrinePrep:
    salt_g: float
    water_ml: float
    relative_density: float
    label: str


def brine_for_salinity(salt_g: float, water_ml: float) -> BrinePrep:
    """Relative density of a brine from salt mass and water volume.

    Uses the linear approximation: rho_rel ~= 1 + 0.0007 * (g salt per mL).
    Accurate to ~1 % in the 0-30 % salinity band, which is all DAPE needs.
    """
    if water_ml <= 0 or salt_g < 0:
        raise ValueError("invalid brine inputs")
    g_per_ml = salt_g / water_ml
    rho = 1.0 + 0.0007 * g_per_ml * 100.0  # per 100 mL reference
    return BrinePrep(salt_g=salt_g, water_ml=water_ml, relative_density=round(rho, 4),
                     label=f"{salt_g:.0f} g garam / {water_ml:.0f} mL air")


def archimedes_sink_ratio(sink_count: int, neutral_count: int, float_count: int,
                          neutral_weight: float = 0.5) -> float:
    """Weighted sink ratio in [0,1]. Neutrals count half. Raises on empty set."""
    total = sink_count + neutral_count + float_count
    if total <= 0:
        raise ValueError("no fruits counted")
    return (sink_count + neutral_weight * neutral_count) / total


def grade_from_sink_ratio(sink_ratio: float) -> str:
    """Map sink ratio to a physical-quality grade. This is the *physics* grade;
    the final grade also considers curvature/colour/defects (see grading.py)."""
    if sink_ratio >= 0.80:
        return "A+"
    if sink_ratio >= 0.65:
        return "A"
    if sink_ratio >= 0.45:
        return "B"
    return "BS"
