"""Spectral / colorimetric physics for deterministic photometry.

Two responsibilities:
  1. sRGB <-> linear <-> CIELAB conversion (colour-space correctness; the DAPE
     dark-chamber pipeline reasons in CIELAB, not raw RGB).
  2. Applying a device white-reference matrix to cancel LED-flash spectral bias
     between phone brands (mitigation for the "flash differs per brand" risk).
"""
from __future__ import annotations

import math

# D65 reference white (2 degree observer)
_XN, _YN, _ZN = 95.047, 100.0, 108.883


def _srgb_to_linear(c: float) -> float:
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(c: float) -> float:
    return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055


def _f(t: float) -> float:
    delta = 6.0 / 29.0
    return t ** (1 / 3) if t > delta ** 3 else t / (3 * delta ** 2) + 4.0 / 29.0


def rgb_to_xyz(r: float, g: float, b: float) -> tuple[float, float, float]:
    """sRGB (0-255) -> CIE XYZ (D65)."""
    rl, gl, bl = _srgb_to_linear(r), _srgb_to_linear(g), _srgb_to_linear(b)
    x = rl * 0.4124 + gl * 0.3576 + bl * 0.1805
    y = rl * 0.2126 + gl * 0.7152 + bl * 0.0722
    z = rl * 0.0193 + gl * 0.1192 + bl * 0.9505
    return x * 100.0, y * 100.0, z * 100.0


def xyz_to_lab(x: float, y: float, z: float) -> tuple[float, float, float]:
    fx, fy, fz = _f(x / _XN), _f(y / _YN), _f(z / _ZN)
    l = 116.0 * fy - 16.0
    a = 500.0 * (fx - fy)
    b = 200.0 * (fy - fz)
    return l, a, b


def rgb_to_lab(r: float, g: float, b: float) -> tuple[float, float, float]:
    return xyz_to_lab(*rgb_to_xyz(r, g, b))


def apply_white_reference(rgb: tuple[float, float, float],
                          white_ref: tuple[float, float, float]) -> tuple[float, float, float]:
    """Per-channel gain correction using a captured white-paper reference.

    white_ref is the *measured* RGB of a known-white target under the device
    LED flash. Dividing by it normalises flash spectral bias so that two phones
    photographing the same reagent patch produce the same CIELAB values.
    """
    if any(w <= 0 for w in white_ref):
        raise ValueError("white reference channels must be > 0")
    return tuple(min(255.0, max(0.0, c / w * 255.0)) for c, w in zip(rgb, white_ref))


def delta_e(lab1: tuple[float, float, float],
            lab2: tuple[float, float, float]) -> float:
    """CIE76 colour difference (sufficient for reagent-patch matching)."""
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(lab1, lab2)))


def match_reagent_curve(measured_lab: tuple[float, float, float],
                        curve: list[tuple[float, tuple[float, float, float]]]) -> float:
    """Given a calibration curve [(value, lab), ...] find the value whose lab is
    closest (min delta-E) to the measured lab. Linear-interpolates the two
    nearest nodes for sub-node resolution.
    """
    if not curve:
        raise ValueError("calibration curve is empty")
    if len(curve) == 1:
        return curve[0][0]
    ranked = sorted(curve, key=lambda node: delta_e(measured_lab, node[1]))
    (v1, lab1), (v2, lab2) = ranked[0], ranked[1]
    d1, d2 = delta_e(measured_lab, lab1), delta_e(measured_lab, lab2)
    if d1 + d2 == 0:
        return v1
    # Weighted inverse-distance interpolation
    return (v1 * d2 + v2 * d1) / (d1 + d2)
