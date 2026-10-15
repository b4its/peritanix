"""Topographic Wetness Index (TWI) — deterministic hydrology.

TWI = ln(a / tan(beta))

where:
    a    = local upslope contributing area per unit contour width (m^2/m)
    beta = local slope angle (radians)

High TWI => concave, wet, water-accumulating positions (drainage troughs) =>
higher risk of *Phytophthora* / *Ralstonia* (soil/water-borne pathogens).
This function is intentionally guarded against beta -> 0 (flat) which would
send tan(beta) -> 0 and TWI -> +inf.
"""
from __future__ import annotations

import math

# SRTM 30 m is coarse: a floor on slope prevents absurd TWI spikes and encodes
# the practical reality that a *real* flat bed still drains slowly.
_MIN_SLOPE_BETA_RAD = math.radians(0.1)  # 0.1 degree floor


def twi(upslope_area_m2_per_m: float, slope_beta_rad: float) -> float:
    """Compute TWI. Raises ValueError on non-physical inputs."""
    if upslope_area_m2_per_m <= 0:
        raise ValueError("upslope_area must be > 0")
    beta = max(abs(slope_beta_rad), _MIN_SLOPE_BETA_RAD)
    return math.log(upslope_area_m2_per_m / math.tan(beta))


def twi_from_degrees(upslope_area_m2_per_m: float, slope_beta_deg: float) -> float:
    return twi(upslope_area_m2_per_m, math.radians(slope_beta_deg))


def drainage_risk_class(twi_value: float) -> str:
    """Map TWI to an agronomic risk band with a prescribed action."""
    if twi_value >= 10.0:
        return "CRITICAL"     # must raise bed >= 50 cm; Phytophthora/Ralstonia vector
    if twi_value >= 8.0:
        return "HIGH"
    if twi_value >= 6.0:
        return "MODERATE"
    return "LOW"


def required_bed_height_cm(twi_value: float) -> int:
    """Prescriptive bed height (the DAPE interference)."""
    if twi_value >= 10.0:
        return 50
    if twi_value >= 8.0:
        return 40
    if twi_value >= 6.0:
        return 30
    return 20


def flow_accumulation(dem: list[list[float]], fill_epsilon: float = 1e-3) -> list[list[float]]:
    """D8 flow accumulation on a small grid (deterministic, no I/O).

    Uses a priority-flood-free, simple steepest-descent routing. For the small
    rasters DAPE delivers (< 500 KB) this is adequate and fully deterministic.
    Cells are processed in order of decreasing elevation (higher drains first).
    """
    import numpy as np

    dem_arr = np.asarray(dem, dtype=float)
    rows, cols = dem_arr.shape
    acc = np.ones_like(dem_arr, dtype=float)  # every cell contributes 1

    # Order cells by descending elevation so upstream cells are processed before
    # their downstream neighbours. np.argsort is ascending -> reverse it. Add the
    # flat index as a tie-breaker to guarantee a fully deterministic order.
    flat = dem_arr.ravel()
    flat_idx = np.arange(flat.size)
    # lexsort keys: last key is primary. Primary = -elevation, secondary = flat idx.
    order = np.lexsort((flat_idx, -flat))

    # 8-neighbour offsets matching (diagonal distance = sqrt(2))
    offsets = [(-1, -1, math.sqrt(2)), (-1, 0, 1.0), (-1, 1, math.sqrt(2)),
               (0, -1, 1.0), (0, 1, 1.0),
               (1, -1, math.sqrt(2)), (1, 0, 1.0), (1, 1, math.sqrt(2))]

    for fi in order:
        r, c = divmod(int(fi), cols)
        z = dem_arr[r, c]
        best = None
        best_slope = 0.0
        for dr, dc, dist in offsets:
            nr, nc = r + dr, c + dc
            if 0 <= nr < rows and 0 <= nc < cols:
                drop = z - dem_arr[nr, nc]
                if drop > 0:
                    slope = drop / dist
                    if slope > best_slope:
                        best_slope = slope
                        best = (nr, nc)
        if best is not None:
            acc[best[0], best[1]] += acc[r, c]

    return acc.tolist()
