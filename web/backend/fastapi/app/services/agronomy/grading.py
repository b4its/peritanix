"""Automated post-harvest grading — deterministic quality scoring.

Given a segmented fruit silhouette (as a binary mask or contour points) plus a
mean colour, compute the three physical quality descriptors from the design doc:

- curvature_ratio : arc length of the fruit spine / chord length (straightness);
                    a straight fruit ~ 1.0, a strongly curved one >> 1.0.
- red_pct         : percentage of the fruit area that is ripe red (a* channel
                    above a CIE-Lab threshold).
- defect_pct      : percentage of area flagged as patek/blemish (dark/necrotic).

Final grade uses a transparent, auditable rule table (Grade A+ / A / B / BS)
rather than a black-box model, so a buyer can defend the result.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class GradeResult:
    curvature_ratio: float
    red_pct: float
    defect_pct: float
    grade: str
    reasons: list[str]


def curvature_ratio(contour: list[tuple[float, float]]) -> float:
    """Arc-length / chord-length of a fitted spine through contour midpoints.

    We approximate the fruit spine by the sequence of vertical midpoints of the
    contour (works for a fruit photographed lengthwise on a contrast mat).
    """
    if len(contour) < 4:
        raise ValueError("contour needs at least 4 points")
    pts = np.asarray(contour, dtype=np.float64)
    # Sort by the principal axis (long axis) via PCA.
    centered = pts - pts.mean(axis=0)
    _, _, vt = np.linalg.svd(centered, full_matrices=False)
    long_axis = vt[0]
    proj = centered @ long_axis
    perp = centered @ vt[1]
    order = np.argsort(proj)
    proj_s, perp_s = proj[order], perp[order]

    # Midpoint spine: bin along the long axis, take mean perpendicular offset.
    n_bins = 12
    edges = np.linspace(proj_s.min(), proj_s.max(), n_bins + 1)
    spine = []
    for i in range(n_bins):
        sel = (proj_s >= edges[i]) & (proj_s < edges[i + 1] if i < n_bins - 1 else proj_s <= edges[i + 1])
        if np.any(sel):
            spine.append((0.5 * (edges[i] + edges[i + 1]), float(perp_s[sel].mean())))
    if len(spine) < 2:
        return 1.0
    spine_arr = np.asarray(spine, dtype=np.float64)
    # Arc length
    seg = np.diff(spine_arr, axis=0)
    arc = float(np.sum(np.hypot(seg[:, 0], seg[:, 1])))
    chord = float(spine_arr[-1, 0] - spine_arr[0, 0])
    if chord <= 1e-6:
        return 1.0
    return arc / chord


def red_percentage(mean_a_channel: float, area: float,
                   full_red_a: float = 45.0, threshold_a: float = 25.0) -> float:
    """Estimate ripe-red coverage from the mean Lab a* of the fruit.

    Linear ramp from threshold_a (0 %) to full_red_a (100 %), clamped.
    """
    if full_red_a <= threshold_a:
        raise ValueError("full_red_a must exceed threshold_a")
    frac = (mean_a_channel - threshold_a) / (full_red_a - threshold_a)
    return float(np.clip(frac, 0.0, 1.0) * 100.0)


def defect_percentage(lesion_area: float, total_area: float) -> float:
    if total_area <= 0:
        raise ValueError("total_area must be > 0")
    return float(np.clip(lesion_area / total_area * 100.0, 0.0, 100.0))


def grade_fruit(curv: float, red_pct: float, defect_pct: float) -> GradeResult:
    """Transparent grade table.

    The order matters: defects disqualify first (a single patek fruit spoils a
    crate), then ripeness, then shape.
    """
    reasons: list[str] = []
    if defect_pct >= 15.0:
        reasons.append(f"cacat tinggi {defect_pct:.1f}% (risiko patek)")
        return GradeResult(curv, red_pct, defect_pct, "BS", reasons)
    if defect_pct >= 5.0:
        reasons.append(f"cacat sedang {defect_pct:.1f}%")

    if red_pct >= 90.0 and defect_pct < 3.0 and curv <= 1.12:
        grade = "A+"
        reasons.append("merah penuh, lurus, bersih")
    elif red_pct >= 75.0 and defect_pct < 5.0 and curv <= 1.25:
        grade = "A"
        reasons.append("merah baik, sedikit lengkung")
    elif red_pct >= 50.0 and defect_pct < 10.0:
        grade = "B"
        reasons.append("setengah matang / lengkung jelas")
    else:
        grade = "BS"
        reasons.append("mutu sortiran")
    return GradeResult(curv, red_pct, defect_pct, grade, reasons)


def grade_from_measurements(contour: list[tuple[float, float]], mean_a_channel: float,
                            lesion_area: float, total_area: float) -> GradeResult:
    curv = curvature_ratio(contour)
    red = red_percentage(mean_a_channel, total_area)
    defect = defect_percentage(lesion_area, total_area)
    return grade_fruit(curv, red, defect)
