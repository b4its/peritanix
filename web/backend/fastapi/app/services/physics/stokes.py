"""Stokes' Law sedimentation -> soil texture (USDA triangle) -> CEC estimate.

A soil/water/dispersant suspension is shaken and photographed twice:
  t = 40 s    -> sand has settled
  t = 7200 s  -> silt has settled; remaining is clay

Stokes terminal velocity:
    v = (2/9) * (rho_p - rho_f) * g * r^2 / mu

We work with *layer thickness fractions* measured by sub-pixel edge detection
on the mobile image, then map (sand, silt, clay) to the USDA texture class and
estimate cation-exchange capacity (CEC, meq/100g).
"""
from __future__ import annotations

import math
from dataclasses import dataclass

# --- Physical constants (23 C, water) ---
G = 9.80665             # m/s^2
RHO_PARTICLE = 2650.0   # kg/m^3 (quartz-dominated soil)
RHO_FLUID = 998.0       # kg/m^3
MU_FLUID = 1.002e-3     # Pa.s

# Settling times prescribed by the protocol (seconds)
T_SAND = 40.0
T_SILT = 7200.0


@dataclass(frozen=True)
class TextureResult:
    sand_pct: float
    silt_pct: float
    clay_pct: float
    soil_class: str
    cec_meq: float


def stokes_velocity(particle_radius_m: float) -> float:
    """Terminal settling velocity (m/s) for a sphere via Stokes' law."""
    if particle_radius_m <= 0:
        raise ValueError("particle radius must be > 0")
    return (2.0 / 9.0) * (RHO_PARTICLE - RHO_FLUID) * G * \
        (particle_radius_m ** 2) / MU_FLUID


def stokes_diameter_for_time(t_seconds: float) -> float:
    """Diameter (m) of the largest particle still suspended after t seconds.

    Invert Stokes: r = sqrt(9*mu*h / (2*(rho_p-rho_f)*g*t)) with h being the
    settling depth; we use unit depth to obtain a per-metre characteristic size,
    which is what the protocol's fixed bottle height normalises to.
    """
    if t_seconds <= 0:
        raise ValueError("time must be > 0")
    # Characteristic diameter for a 1 m settling depth (protocol uses fixed bottle)
    h = 1.0
    r = math.sqrt(9.0 * MU_FLUID * h / (2.0 * (RHO_PARTICLE - RHO_FLUID) * G * t_seconds))
    return 2.0 * r


def classify_usda(sand_pct: float, silt_pct: float, clay_pct: float) -> str:
    """USDA soil texture triangle (simplified, ordered by dominant+clay rules)."""
    total = sand_pct + silt_pct + clay_pct
    if total <= 0:
        raise ValueError("texture fractions must sum to > 0")
    # Normalise to 100 %
    s = sand_pct / total * 100.0
    si = silt_pct / total * 100.0
    cl = clay_pct / total * 100.0

    if cl >= 40 and si < 40 and s <= 45:
        if cl >= 60:
            return "clay"
        if si >= 40:
            return "silty clay"
        return "clay"
    if cl >= 35 and s > 45:
        return "sandy clay"
    if 27 <= cl < 40 and s <= 20 and si < 40:
        return "silty clay loam"
    if 27 <= cl < 40 and 20 < s <= 45:
        return "clay loam"
    if 20 <= cl < 35 and si >= 40 and s <= 20:
        return "silt loam"
    if si >= 50 and cl < 27:
        return "silt loam"
    if 7 <= cl < 27 and 28 <= si < 50 and s <= 52:
        return "loam"
    if si >= 28 and cl < 27 and s > 52:
        return "sandy loam"
    if s >= 70 and cl < 15:
        return "sand" if si < 15 else "loamy sand"
    if s >= 43 and cl < 20:
        return "sandy loam"
    return "loam"


# CEC by texture class midpoint (meq/100g) — standard agronomic reference values
_CEC_BY_CLASS = {
    "sand": 3.0, "loamy sand": 5.0, "sandy loam": 10.0, "loam": 15.0,
    "silt loam": 16.0, "silt": 20.0, "sandy clay loam": 18.0,
    "clay loam": 24.0, "silty clay loam": 26.0, "sandy clay": 25.0,
    "silty clay": 34.0, "clay": 35.0,
}


def cec_from_texture(sand_pct: float, silt_pct: float, clay_pct: float) -> float:
    """Estimate CEC (meq/100g). Organic-matter contribution is folded into a
    small positive bias typical of tropical Andisols/Inceptisols chilli beds."""
    cls = classify_usda(sand_pct, silt_pct, clay_pct)
    base = _CEC_BY_CLASS.get(cls, 15.0)
    total = sand_pct + silt_pct + clay_pct
    clay_frac = clay_pct / total if total else 0.0
    return round(base * (0.6 + 0.4 * clay_frac / 0.4) if clay_frac else base, 2)


def classify_from_layers(sand_thickness: float, silt_thickness: float,
                         clay_thickness: float) -> TextureResult:
    """Full pipeline: measured layer thicknesses -> USDA class + CEC."""
    total = sand_thickness + silt_thickness + clay_thickness
    if total <= 0:
        raise ValueError("layer thicknesses must sum to > 0")
    sand_pct = sand_thickness / total * 100.0
    silt_pct = silt_thickness / total * 100.0
    clay_pct = clay_thickness / total * 100.0
    cls = classify_usda(sand_pct, silt_pct, clay_pct)
    cec = cec_from_texture(sand_pct, silt_pct, clay_pct)
    return TextureResult(
        sand_pct=round(sand_pct, 1),
        silt_pct=round(silt_pct, 1),
        clay_pct=round(clay_pct, 1),
        soil_class=cls,
        cec_meq=cec,
    )
