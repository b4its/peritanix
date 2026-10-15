"""Stem-dip Ooze Test — Dense Optical Flow (Ralstonia vs Fusarium).

When a wilting chilli stem base is cut and dipped in clear water against a dark
background, Ralstonia solanacearum exudes a milky **bacterial streaming** — a
coherent, directional mass flow of white threads. Fusarium oxysporum does not
produce this streaming (its vascular discolouration stays internal).

Deterministic core: **Lucas–Kanade optical flow** on a 10 s video's frames,
then physical descriptors of the flow field:

- mean_speed      : average particle displacement per frame (px)
- coherence       : |mean velocity vector| / mean speed  (1 = perfectly aligned)
- directionality  : concentration of flow angles (circular variance)
- streaming_score: combined heuristic; high => bacterial streaming => Ralstonia

Lucas–Kanade is solved in closed form per pixel-window (numpy), deterministic,
and matches the OpenCV `calcOpticalFlowPyrLK`/`DenseFlow` used on-device.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class OozeResult:
    mean_speed: float
    coherence: float
    directionality: float
    streaming_score: float
    verdict: str
    confidence: float


def _to_gray(frame: np.ndarray) -> np.ndarray:
    f = np.asarray(frame, dtype=np.float64)
    if f.ndim == 2:
        return f
    return 0.299 * f[..., 0] + 0.587 * f[..., 1] + 0.114 * f[..., 2]


def lucas_kanade(ref: np.ndarray, mov: np.ndarray, win: int = 7,
                 step: int = 2) -> np.ndarray:
    """Dense Lucas–Kanade flow. Returns an (H, W, 2) field of (vx, vy).

    px, py are the partial derivatives (Sobel-style central differences),
    solved per window by least squares. Deterministic; no randomness.
    """
    ref = ref.astype(np.float64)
    mov = mov.astype(np.float64)
    h, w = ref.shape
    rad = win // 2

    # Space-time gradients between the two frames.
    ix = np.zeros_like(ref)
    iy = np.zeros_like(ref)
    it = mov - ref
    ix[:, 1:-1] = (ref[:, 2:] - ref[:, :-2]) / 2.0
    iy[1:-1, :] = (ref[2:, :] - ref[:-2, :]) / 2.0

    flow = np.zeros((h, w, 2), dtype=np.float64)

    for y in range(rad, h - rad, step):
        for x in range(rad, w - rad, step):
            win_ix = ix[y - rad:y + rad + 1, x - rad:x + rad + 1].ravel()
            win_iy = iy[y - rad:y + rad + 1, x - rad:x + rad + 1].ravel()
            win_it = it[y - rad:y + rad + 1, x - rad:x + rad + 1].ravel()
            a = np.stack([win_ix, win_iy], axis=1)
            at_a = a.T @ a
            det = at_a[0, 0] * at_a[1, 1] - at_a[0, 1] * at_a[1, 0]
            if abs(det) < 1e-6:
                continue  # aperture problem / flat region
            b = -(a.T @ win_it)
            vx = (at_a[1, 1] * b[0] - at_a[0, 1] * b[1]) / det
            vy = (-at_a[1, 0] * b[0] + at_a[0, 0] * b[1]) / det
            flow[y, x, 0] = vx
            flow[y, x, 1] = vy

    return flow


def analyse_ooze(frames: list[np.ndarray], speed_floor: float = 0.05) -> OozeResult:
    """Analyse a video (list of frames) for bacterial streaming.

    Computes flow between consecutive frames, aggregates all motion vectors
    above `speed_floor` (to ignore camera noise), and derives the descriptors.
    """
    if len(frames) < 2:
        raise ValueError("need at least 2 frames")

    vxs: list[np.ndarray] = []
    vys: list[np.ndarray] = []
    for i in range(len(frames) - 1):
        ref = _to_gray(frames[i])
        mov = _to_gray(frames[i + 1])
        if ref.shape != mov.shape:
            raise ValueError("all frames must share the same shape")
        flow = lucas_kanade(ref, mov)
        vxs.append(flow[..., 0].ravel())
        vys.append(flow[..., 1].ravel())

    vx = np.concatenate(vxs)
    vy = np.concatenate(vys)
    speed = np.hypot(vx, vy)
    mask = speed > speed_floor
    if not np.any(mask):
        return OozeResult(0.0, 0.0, 0.0, 0.0, "Fusarium (no streaming)", 0.55)

    vx_m = vx[mask]
    vy_m = vy[mask]
    sp = np.hypot(vx_m, vy_m)
    mean_speed = float(sp.mean())

    # Coherence: length of the mean vector over mean individual magnitude.
    mean_vec = math.hypot(float(vx_m.mean()), float(vy_m.mean()))
    coherence = mean_vec / mean_speed if mean_speed > 0 else 0.0

    # Directionality: 1 - circular variance of angles.
    angles = np.arctan2(vy_m, vx_m)
    r = math.hypot(float(np.cos(angles).mean()), float(np.sin(angles).mean()))
    directionality = r  # resultant length in [0,1]

    streaming_score = float(np.clip(0.5 * coherence + 0.5 * directionality, 0.0, 1.0))
    if streaming_score >= 0.6:
        verdict = "Ralstonia (bacterial streaming)"
        confidence = min(0.99, 0.5 + streaming_score / 2)
    else:
        verdict = "Fusarium (no bacterial streaming)"
        confidence = min(0.99, 0.5 + (1 - streaming_score) / 2)

    return OozeResult(
        mean_speed=round(mean_speed, 4),
        coherence=round(coherence, 4),
        directionality=round(directionality, 4),
        streaming_score=round(streaming_score, 4),
        verdict=verdict,
        confidence=round(confidence, 4),
    )
