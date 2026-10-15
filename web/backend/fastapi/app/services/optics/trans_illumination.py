"""Active Leaf Trans-Illumination — mesophyll attenuation analysis.

With the LED flash pressed against the leaf (0 cm, contact mode), transmitted
light reveals internal mesophyll structure. Latent fungal colonisation
(Colletotrichum / Cercospora) densifies tissue and lowers transmittance in
localised spots 3–4 days before surface symptoms.

Deterministic core:
1. Compute the optical-density map from the transmitted intensity:
       OD = -log10(I / I0)          (I0 = bright reference, typically the petiole
                                     ridge or a non-lesion patch)
2. Segment dark (high-OD) blobs via a robust Otsu threshold.
3. Report `mesophyll_transmittance_attenuation_pct` = fraction of the leaf area
   whose OD exceeds the healthy baseline by > k * sigma.

The learned classifier (CNN) that maps attenuation patterns to pathologies is a
swap-in point; here we produce the quantitative physical metric it consumes.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class TransIllumResult:
    attenuation_pct: float
    lesion_fraction: float
    mean_od: float
    max_od: float
    otsu_threshold: float
    latent_fungal_suspected: bool


def _otsu_threshold(values: np.ndarray, bins: int = 256) -> float:
    """Otsu's method — deterministic, parameter-free two-class threshold."""
    hist, edges = np.histogram(values, bins=bins)
    hist = hist.astype(np.float64)
    total = hist.sum()
    if total == 0:
        return 0.0
    prob = hist / total
    omega = np.cumsum(prob)
    mu = np.cumsum(prob * (edges[:-1] + edges[1:]) / 2.0)
    mu_t = mu[-1]
    denom = omega * (1.0 - omega)
    denom[denom == 0] = 1e-12
    sigma_b = (mu_t * omega - mu) ** 2 / denom
    idx = int(np.argmax(sigma_b))
    return float((edges[idx] + edges[idx + 1]) / 2.0)


def trans_illumination(intensity: np.ndarray, leaf_mask: np.ndarray | None = None,
                       baseline_percentile: float = 90.0,
                       sigma_k: float = 2.0) -> TransIllumResult:
    """Analyse a trans-illuminated leaf image.

    Args:
        intensity: 2-D array of transmitted intensity (0..255 or 0..1).
        leaf_mask: optional boolean mask of the leaf region.
        baseline_percentile: percentile of OD used as the healthy baseline.
        sigma_k: how many baseline sigmas above the reference marks attenuation.

    Returns:
        TransIllumResult with the attenuation metric and a fungal-suspicion flag.
    """
    img = np.asarray(intensity, dtype=np.float64)
    if img.ndim != 2:
        raise ValueError("intensity must be a 2-D grayscale array")
    if img.max() > 1.5:
        img = img / 255.0

    if leaf_mask is None:
        leaf_mask = np.ones_like(img, dtype=bool)
    else:
        leaf_mask = np.asarray(leaf_mask, dtype=bool)
        if leaf_mask.shape != img.shape:
            raise ValueError("leaf_mask shape must match intensity")

    eps = 1e-6
    i0 = np.percentile(img[leaf_mask], baseline_percentile)
    i0 = max(i0, eps)
    od = -np.log10(np.clip(img, eps, 1.0) / i0)
    od_leaf = od[leaf_mask]

    baseline = float(np.percentile(od_leaf, 50.0))
    sigma = float(np.std(od_leaf)) or 1e-6
    thresh = baseline + sigma_k * sigma

    attenuated = (od > thresh) & leaf_mask
    attenuation_pct = float(attenuated.sum()) / max(leaf_mask.sum(), 1) * 100.0

    otsu = _otsu_threshold(od_leaf)
    lesion_fraction = float(((od_leaf > otsu).sum()) / max(od_leaf.size, 1)) * 100.0

    # Latent fungal suspicion: meaningful attenuation spread but not the whole leaf.
    suspected = bool(3.0 <= attenuation_pct <= 60.0)

    return TransIllumResult(
        attenuation_pct=round(attenuation_pct, 3),
        lesion_fraction=round(lesion_fraction, 3),
        mean_od=round(float(od_leaf.mean()), 4),
        max_od=round(float(od_leaf.max()), 4),
        otsu_threshold=round(otsu, 4),
        latent_fungal_suspected=suspected,
    )
