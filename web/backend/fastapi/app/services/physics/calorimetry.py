"""Black's Law (calorimetry) — deterministic post-harvest sterilization.

Goal: reach a *hot-water bath of exactly 52 C* to kill latent Colletotrichum
(patek) spores without a thermometer, at any elevation.

Water boiling point falls with altitude (barometric):
    T_boil(h) = 100 - 0.0033 * h        (h in metres)

Black's Law for mixing boiling water (V_boil) with well water (V_well):
    V_boil * (T_boil - T_target) = V_well * (T_target - T_well)

We solve for the *ratio* so the app can say "5 gayung didih : 9 gayung sumur".
"""
from __future__ import annotations

from dataclasses import dataclass

TARGET_DEFAULT_C = 52.0
BOIL_LAPSE_PER_M = 0.0033   # deg C of boiling-point drop per metre of elevation


@dataclass(frozen=True)
class HotWaterRatio:
    boiling_point_c: float
    target_c: float
    well_temp_c: float
    v_boil_parts: float
    v_well_parts: float
    ratio_label: str
    # Full-precision values (unrounded) for exact forward-model verification.
    v_boil_exact: float = 0.0
    v_well_exact: float = 0.0


def boiling_point(elevation_m: float) -> float:
    """Local boiling point of water (C) at the given elevation."""
    return 100.0 - BOIL_LAPSE_PER_M * elevation_m


def hot_water_ratio(elevation_m: float,
                    well_temp_c: float,
                    target_c: float = TARGET_DEFAULT_C,
                    scale: float = 1.0) -> HotWaterRatio:
    """Compute the boiling:well water mixing ratio to hit target_c exactly.

    Returns parts normalised so that the well-water part equals `scale`
    (e.g. scale=9 -> returns boil part relative to 9 well parts).
    """
    t_boil = boiling_point(elevation_m)
    if not (well_temp_c < target_c < t_boil):
        raise ValueError(
            f"Unreachable target: need well_temp < target < boiling "
            f"({well_temp_c} < {target_c} < {t_boil:.3f})"
        )
    # From Black's Law: V_boil * (t_boil - target) = V_well * (target - t_well)
    v_well = scale
    v_boil = v_well * (target_c - well_temp_c) / (t_boil - target_c)
    return HotWaterRatio(
        boiling_point_c=round(t_boil, 3),
        target_c=target_c,
        well_temp_c=well_temp_c,
        v_boil_parts=round(v_boil, 2),
        v_well_parts=round(v_well, 2),
        ratio_label=f"{v_boil:.1f} gayung didih : {v_well:.0f} gayung sumur",
        v_boil_exact=v_boil,
        v_well_exact=float(v_well),
    )


def mix_temperature(elevation_m: float, v_boil: float, well_temp_c: float,
                    v_well: float) -> float:
    """Forward model: resulting temperature of a given mix (for verification)."""
    t_boil = boiling_point(elevation_m)
    if v_boil + v_well <= 0:
        raise ValueError("volumes must be > 0")
    return (v_boil * t_boil + v_well * well_temp_c) / (v_boil + v_well)
