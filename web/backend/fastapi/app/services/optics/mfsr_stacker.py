"""Multi-Frame Super-Resolution (MFSR) burst stacking — deterministic core.

The mobile app captures a 12-frame YUV burst in ~0.4 s. This module:

1. Aligns frames by integer translation via **phase correlation** (FFT-based,
   deterministic, no learned weights) — a robust, cheap estimator for the small
   sub-pixel/near-integer shifts caused by handheld capture.
2. Shifts-and-adds with a Laplacian sharpness gate that rejects blurred frames.
3. Reports `mfsr_sharpness_gain` = Laplacian-variance(stacked) / mean(variance(frames)).

Phase correlation is exact for translation and needs only numpy.FFT, so it runs
identically on the server and (translated) on-device in the Kotlin/OpenCV path.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class MfsrResult:
    stacked_shape: tuple[int, int]
    frames_used: int
    frames_rejected: int
    shifts: list[tuple[float, float]]
    sharpness_gain: float
    mean_frame_sharpness: float
    stacked_sharpness: float


def laplacian_variance(gray: np.ndarray) -> float:
    """Variance of the Laplacian — a standard, parameter-free sharpness metric.

    High value => crisp edges. Deterministic, no thresholds beyond the kernel.
    """
    if gray.dtype != np.float64:
        gray = gray.astype(np.float64)
    # 4-neighbour Laplacian kernel
    lap = (
        -4.0 * gray[1:-1, 1:-1]
        + gray[0:-2, 1:-1]
        + gray[2:, 1:-1]
        + gray[1:-1, 0:-2]
        + gray[1:-1, 2:]
    )
    return float(lap.var())


def _to_gray(frame: np.ndarray) -> np.ndarray:
    if frame.ndim == 2:
        return frame.astype(np.float64)
    # Rec. 601 luma
    return (0.299 * frame[..., 0] + 0.587 * frame[..., 1] + 0.114 * frame[..., 2]).astype(np.float64)


def phase_correlation_shift(ref: np.ndarray, mov: np.ndarray) -> tuple[float, float]:
    """Estimate integer (dx, dy) translation of `mov` relative to `ref`.

    Uses the peak of the (windowed) cross-power spectrum. Returns the shift the
    moving frame must be translated by to match the reference.
    """
    a = np.fft.fft2(ref - ref.mean())
    b = np.fft.fft2(mov - mov.mean())
    cross = a * np.conj(b)
    # Normalise (phase correlation) with a small epsilon to avoid /0.
    denom = np.abs(cross) + 1e-12
    r = np.fft.ifft2(cross / denom).real
    peak = np.unravel_index(np.argmax(r), r.shape)
    dy, dx = peak
    h, w = ref.shape
    # Wrap negative frequencies to the [-N/2, N/2) range.
    if dy > h // 2:
        dy -= h
    if dx > w // 2:
        dx -= w
    return float(dx), float(dy)


def _shift(img: np.ndarray, dx: int, dy: int) -> np.ndarray:
    """Integer shift with edge clamping (np.roll + border fix)."""
    out = np.roll(np.roll(img, dy, axis=0), dx, axis=1)
    # Blank the wrapped edges so they do not create false high-frequency edges.
    if dy > 0:
        out[:dy, :] = img[0, :]
    elif dy < 0:
        out[dy:, :] = img[-1, :]
    if dx > 0:
        out[:, :dx] = img[:, 0:1]
    elif dx < 0:
        out[:, dx:] = img[:, -1:]
    return out


def stack_burst(frames: np.ndarray, sharpness_reject_ratio: float = 0.5) -> MfsrResult:
    """Align and stack a burst.

    Args:
        frames: array of shape (N, H, W) or (N, H, W, C).
        sharpness_reject_ratio: reject a frame if its Laplacian variance is below
            this fraction of the sharpest frame's variance.

    Returns:
        MfsrResult with the stacked image dimensions, per-frame shifts and the
        sharpness gain relative to the mean single-frame sharpness.
    """
    arr = np.asarray(frames)
    if arr.ndim < 3:
        raise ValueError("burst must have shape (N>=2, H, W[, C])")
    n = arr.shape[0]
    if n < 2:
        raise ValueError("need at least 2 frames for MFSR")

    grays = np.stack([_to_gray(f) for f in arr])  # (N,H,W)
    sharp = np.array([laplacian_variance(g) for g in grays])
    ref_idx = int(np.argmax(sharp))
    ref = grays[ref_idx]

    reject_thresh = sharp[ref_idx] * sharpness_reject_ratio
    shifts: list[tuple[float, float]] = []
    used = 0
    rejected = 0
    acc_sum: np.ndarray | None = None
    acc_n = 0

    for i in range(n):
        if i != ref_idx and sharp[i] < reject_thresh:
            shifts.append((0.0, 0.0))
            rejected += 1
            continue
        if i == ref_idx:
            dx = dy = 0
        else:
            dx, dy = phase_correlation_shift(ref, grays[i])
        dx_i, dy_i = round(dx), round(dy)
        shifts.append((dx, dy))
        aligned = _shift(grays[i], dx_i, dy_i)
        acc_sum = aligned if acc_sum is None else acc_sum + aligned
        acc_n += 1
        used += 1

    if acc_sum is None or acc_n == 0:
        raise ValueError("all frames rejected")

    stacked = acc_sum / acc_n
    stacked_sharp = laplacian_variance(stacked)
    mean_frame_sharp = float(sharp.mean())
    gain = stacked_sharp / mean_frame_sharp if mean_frame_sharp > 0 else 0.0

    return MfsrResult(
        stacked_shape=(stacked.shape[0], stacked.shape[1]),
        frames_used=used,
        frames_rejected=rejected,
        shifts=shifts,
        sharpness_gain=round(gain, 4),
        mean_frame_sharpness=round(mean_frame_sharp, 4),
        stacked_sharpness=round(stacked_sharp, 4),
    )


def stack_burst_u8(frames: list[list[list[float]]], **kw) -> tuple[np.ndarray, MfsrResult]:
    """Convenience wrapper for JSON-delivered grayscale frames."""
    arr = np.asarray(frames, dtype=np.float64)
    res = stack_burst(arr, **kw)
    # Re-run to also return the stacked image (cheap for small bursts).
    grays = np.stack([_to_gray(f) for f in arr])
    sharp = np.array([laplacian_variance(g) for g in grays])
    ref_idx = int(np.argmax(sharp))
    ref = grays[ref_idx]
    acc = None
    count = 0
    for i in range(arr.shape[0]):
        if i != ref_idx and sharp[i] < sharp[ref_idx] * kw.get("sharpness_reject_ratio", 0.5):
            continue
        dx, dy = (0, 0) if i == ref_idx else phase_correlation_shift(ref, grays[i])
        aligned = _shift(grays[i], round(dx), round(dy))
        acc = aligned if acc is None else acc + aligned
        count += 1
    stacked = acc / count
    return stacked, res
