"""Leaf lesion severity — ONNX pixel-wise segmenter with a deterministic fallback.

`severity_pct` (fraction of leaf area that is chlorotic or necrotic) scales the
prescribed fungicide dose, so it must be both accurate and auditable. This
module runs the registered `severity_unet_lite` ONNX segmenter when its artefact
is present, and otherwise falls back to a transparent colour/texture rule that
mirrors the model's feature space (local mean colour + local variance).

The ONNX model outputs per-pixel probabilities over
[healthy, chlorotic, necrotic, background]; severity is measured over the leaf
region (non-background) as necrotic + 0.5*chlorotic, matching the grading
semantics in services/agronomy/grading.py.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from ..models import inference


@dataclass(frozen=True)
class SeverityResult:
    severity_pct: float
    chlorotic_pct: float
    necrotic_pct: float
    healthy_pct: float
    method: str
    uncertain_pct: float = 0.0
    probabilities: np.ndarray | None = field(default=None, repr=False)


def _box_blur(img: np.ndarray, k: int = 3) -> np.ndarray:
    pad = k // 2
    p = np.pad(img, ((0, 0), (pad, pad), (pad, pad)), mode="reflect")
    out = np.zeros_like(img)
    for dy in range(k):
        for dx in range(k):
            out += p[:, dy:dy + img.shape[1], dx:dx + img.shape[2]]
    return out / (k * k)


def _fallback_probs(img: np.ndarray) -> np.ndarray:
    """Deterministic per-pixel class scores in the same [4,H,W] layout.

    Mirrors the segmenter's learned decision rule (which itself was trained on
    these signatures):
      * background  : dark AND desaturated (soil / contrast mat);
      * necrotic    : dark but still saturated (sunken lesion), texture-boosted;
      * chlorotic   : bright yellow (min(R,G) - B high);
      * healthy     : green tissue.
    Scores are mutually exclusive so argmax is unambiguous and monotone.
    """
    r, g, b = img[0], img[1], img[2]
    blur = _box_blur(img, 3)
    var = _box_blur((img - blur) ** 2, 3).mean(axis=0)
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    sat = mx - mn

    darkness = np.clip(0.22 - lum, 0, 1) / 0.22          # 0 bright .. 1 very dark
    sat_norm = np.clip(sat / 0.25, 0, 1)                 # 0 grey .. 1 saturated
    yellow = np.clip((np.minimum(r, g) - b) / 0.35, 0, 1)  # chlorosis strength
    green = np.clip((g - 0.5 * (r + b)) / 0.35, 0, 1)    # healthy strength

    background = darkness * (1.0 - sat_norm)
    lesion_dark = darkness * sat_norm                    # dark + saturated
    necrotic = lesion_dark * np.clip(1.0 + var * 10.0, 1.0, 2.0)
    chlorotic = yellow * (1.0 - darkness)
    healthy = green * (1.0 - darkness) * (1.0 - yellow)

    stack = np.stack([healthy, chlorotic, necrotic, background], axis=0)
    stack = np.clip(stack, 0, None) + 1e-6
    return stack / stack.sum(axis=0, keepdims=True)


def _severity_from_probs(probs: np.ndarray, leaf_mask: np.ndarray | None) -> SeverityResult:
    classes = np.argmax(probs, axis=0)  # (H,W)
    if leaf_mask is None:
        leaf_mask = classes != 3  # not background
    leaf_mask = np.asarray(leaf_mask, dtype=bool)
    denom = max(int(leaf_mask.sum()), 1)
    chl = float(((classes == 1) & leaf_mask).sum()) / denom * 100.0
    nec = float(((classes == 2) & leaf_mask).sum()) / denom * 100.0
    # uncertain = posterior not confidently one class (max prob < 0.6) on leaf.
    conf = probs.max(axis=0)
    uncertain = float(((conf < 0.6) & leaf_mask).sum()) / denom * 100.0
    severity = nec + 0.5 * chl
    return SeverityResult(
        severity_pct=round(severity, 3),
        chlorotic_pct=round(chl, 3),
        necrotic_pct=round(nec, 3),
        healthy_pct=round(100.0 - chl - nec, 3),
        method="pending",
        uncertain_pct=round(uncertain, 3),
    )


def assess_severity(img: np.ndarray, leaf_mask: np.ndarray | None = None,
                    models_dir: str | Path | None = None) -> SeverityResult:
    """Run the ONNX segmenter if available, else the deterministic fallback.

    Args:
        img: (3,H,W) float RGB in [0,1].
        leaf_mask: optional (H,W) bool leaf region.
        models_dir: registry root (defaults to settings.dape_models_dir).
    """
    img = np.asarray(img, dtype=np.float32)
    if img.ndim != 3 or img.shape[0] != 3:
        raise ValueError("image must be (3,H,W)")
    if img.max() > 1.5:
        img = img / 255.0

    if models_dir is None:
        from ...config import get_settings

        models_dir = get_settings().dape_models_dir

    res = inference.infer(Path(models_dir), "severity_unet_lite",
                          {"image": img[None].astype(np.float32)}, raw=True)
    if res.available and "probabilities" in res.outputs:
        probs = np.asarray(res.outputs["probabilities"])[0]  # (4,H,W)
        out = _severity_from_probs(probs, leaf_mask)
        return SeverityResult(**{**out.__dict__, "method": "onnx_model"})

    fb = _fallback_probs(img)
    out = _severity_from_probs(fb, leaf_mask)
    return SeverityResult(**{**out.__dict__, "method": "deterministic_fallback"})
