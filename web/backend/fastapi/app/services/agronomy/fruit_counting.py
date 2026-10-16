"""Fruit counting by ripeness class — deterministic colour-blob core (Opsi 3).

`yield_projection` needs per-tree counts of three ripeness classes
(green_dark / red_blush / red_full). The eventual on-device YOLOv8-Nano detector
will regress these counts from a canopy photo; until annotated field data is
available, this module provides the *deterministic, auditable* counting core
that (a) works today, and (b) defines the exact contract + colour model the
learned detector is seeded from and validated against.

Method (classical CV, no learned weights):
  1. Convert the RGB canopy image to HSV.
  2. Classify each pixel:
       red_full  : H near 0/180, high saturation, decent value   (ripe red)
       red_blush : H just off red toward orange + partial desat   (turning)
       green_dark: H green band, low-to-mid value                 (mature unripe)
     plus a "barren" class for leaf/stem/background (excluded).
  3. Morphologically open each class mask to remove speckle, label connected
     components (scipy.ndimage), and keep blobs whose area is within a plausible
     fruit-size band.
  4. Return per-class blob counts + mean blob area (a proxy for fruit size).

Everything is closed-form and unit-testable; blobs are counted, not detected by
a black box, so a grower can audit the number.
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
from scipy import ndimage


@dataclass(frozen=True)
class FruitCounts:
    green_dark: int
    red_blush: int
    red_full: int
    total: int
    mean_blob_area_px: dict[str, float] = field(default_factory=dict)
    image_size: tuple[int, int] = (0, 0)


def _rgb_to_hsv(img: np.ndarray) -> np.ndarray:
    """Vectorized RGB→HSV for a (H,W,3) float array in [0,1].

    Returns H in [0,1), S,V in [0,1]. Matches the standard hexagonal model.
    """
    r, g, b = img[..., 0], img[..., 1], img[..., 2]
    mx = np.max(img, axis=-1)
    mn = np.min(img, axis=-1)
    diff = mx - mn
    h = np.zeros_like(mx)
    # Hue sectors
    nonzero = diff > 1e-9
    rc = np.where(nonzero, (mx - r) / np.where(nonzero, diff, 1), 0)
    gc = np.where(nonzero, (mx - g) / np.where(nonzero, diff, 1), 0)
    bc = np.where(nonzero, (mx - b) / np.where(nonzero, diff, 1), 0)
    h = np.where(mx == r, bc - gc, h)
    h = np.where(mx == g, 2.0 + rc - bc, h)
    h = np.where(mx == b, 4.0 + gc - rc, h)
    h = (h / 6.0) % 1.0
    s = np.where(mx > 1e-9, diff / np.where(mx > 1e-9, mx, 1), 0.0)
    return np.stack([h, s, mx], axis=-1)


def _ocean(mask: np.ndarray, iters: int = 1) -> np.ndarray:
    """Binary morphological open (erode then dilate) with a 3x3 cross kernel."""
    def erode(m):
        out = m.copy()
        out[1:, :] &= m[:-1, :]
        out[:-1, :] &= m[1:, :]
        out[:, 1:] &= m[:, :-1]
        out[:, :-1] &= m[:, 1:]
        return out

    def dilate(m):
        out = m.copy()
        out[1:, :] |= m[:-1, :]
        out[:-1, :] |= m[1:, :]
        out[:, 1:] |= m[:, :-1]
        out[:, :-1] |= m[:, 1:]
        return out

    for _ in range(iters):
        mask = erode(mask)
    for _ in range(iters):
        mask = dilate(mask)
    return mask


def classify_pixels(img: np.ndarray) -> dict[str, np.ndarray]:
    """Return boolean masks per ripeness class for an RGB image (H,W,3) in [0,1]."""
    img = np.asarray(img, dtype=np.float64)
    if img.ndim != 3 or img.shape[2] != 3:
        raise ValueError("image must be (H,W,3)")
    if img.max() > 1.5:
        img = img / 255.0
    hsv = _rgb_to_hsv(img)
    h, s, v = hsv[..., 0], hsv[..., 1], hsv[..., 2]

    # Ripe red: hue wraps around 0 (red); allow 0..0.045 and 0.955..1.
    red_hue = (h <= 0.045) | (h >= 0.955)
    # Blush/turning: orange-red band, slightly lower saturation.
    blush_hue = (h > 0.045) & (h <= 0.10)
    # Green (unripe, mature): green/cyan band.
    green_hue = (h >= 0.20) & (h <= 0.45)

    red_full = red_hue & (s >= 0.45) & (v >= 0.25)
    red_blush = blush_hue & (s >= 0.35) & (v >= 0.25)
    # Green (unripe, mature) fruit is hard to separate from foliage by hue alone
    # (both sit in the green band). It IS darker than the surrounding leaves, so
    # we gate green blobs on being darker than their local green neighbourhood.
    green_hue_mask = green_hue & (s >= 0.35) & (v >= 0.08)
    local_v = _local_mean(v, radius=15)
    green_dark = green_hue_mask & (v <= local_v - 0.10)

    return {"green_dark": green_dark, "red_blush": red_blush, "red_full": red_full}


def _local_mean(a: np.ndarray, radius: int = 15) -> np.ndarray:
    """Box mean over a (2r+1)² window via an integral image (O(HW))."""
    r = max(1, int(radius))
    pad = np.pad(a, r, mode="edge")
    ii = pad.cumsum(0).cumsum(1)
    ii = np.pad(ii, ((1, 0), (1, 0)))
    H, W = a.shape
    ys = np.arange(H)
    xs = np.arange(W)
    y0, y1 = ys, ys + 2 * r + 1
    x0, x1 = xs, xs + 2 * r + 1
    total = (ii[np.ix_(y1, x1)] - ii[np.ix_(y0, x1)]
             - ii[np.ix_(y1, x0)] + ii[np.ix_(y0, x0)])
    area = (2 * r + 1) ** 2
    return total / area


def count_fruit(img: np.ndarray, min_area_px: int = 12, max_area_px: int = 20000,
                open_iters: int = 1) -> FruitCounts:
    """Count fruit blobs per ripeness class.

    Args:
        img: (H,W,3) RGB in [0,1] or [0,255].
        min_area_px: blobs smaller than this are discarded (noise).
        max_area_px: blobs larger than this are discarded (merged foliage).
        open_iters: morphological open iterations (speckle removal).

    The area band is the deterministic proxy for "one fruit" in the absence of
    a trained instance detector; it is documented and overridable.
    """
    masks = classify_pixels(img)
    counts: dict[str, int] = {}
    mean_area: dict[str, float] = {}
    for cls, mask in masks.items():
        m = _ocean(mask, open_iters)
        labels, n = ndimage.label(m)
        if n == 0:
            counts[cls] = 0
            mean_area[cls] = 0.0
            continue
        sizes = ndimage.sum(np.ones_like(labels), labels, index=range(1, n + 1))
        kept = [sz for sz in sizes if min_area_px <= sz <= max_area_px]
        counts[cls] = len(kept)
        mean_area[cls] = round(float(np.mean(kept)), 1) if kept else 0.0

    h, w = img.shape[0], img.shape[1]
    return FruitCounts(
        green_dark=counts["green_dark"],
        red_blush=counts["red_blush"],
        red_full=counts["red_full"],
        total=sum(counts.values()),
        mean_blob_area_px=mean_area,
        image_size=(h, w),
    )
