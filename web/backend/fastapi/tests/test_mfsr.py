"""Strict tests: MFSR burst stacking + Laplacian sharpness + phase correlation."""
from __future__ import annotations

import numpy as np
import pytest

from app.services.optics import mfsr_stacker as mfsr


def _sharp_block(n: int = 64, seed: int = 0) -> np.ndarray:
    """A high-contrast synthetic image (checkerboard + gradient)."""
    rng = np.random.default_rng(seed)
    x = np.linspace(0, 4 * np.pi, n)
    img = 128 + 60 * np.sin(x)[None, :] * np.cos(x)[:, None]
    img += rng.normal(0, 1.0, (n, n))
    return img


def _shift_int(img: np.ndarray, dx: int, dy: int) -> np.ndarray:
    return np.roll(np.roll(img, dy, axis=0), dx, axis=1)


class TestLaplacianVariance:
    def test_flat_image_zero(self):
        assert mfsr.laplacian_variance(np.full((32, 32), 100.0)) == pytest.approx(0.0)

    def test_edges_increase_sharpness(self):
        flat = np.full((32, 32), 100.0)
        edge = flat.copy()
        edge[:, 16:] = 200.0
        assert mfsr.laplacian_variance(edge) > mfsr.laplacian_variance(flat)


class TestPhaseCorrelation:
    def test_recovers_known_shift(self):
        ref = _sharp_block()
        mov = _shift_int(ref, 3, -2)
        dx, dy = mfsr.phase_correlation_shift(ref, mov)
        # phase correlation returns the shift of mov relative to ref
        assert (round(dx), round(dy)) == (-3, 2)

    def test_zero_shift(self):
        ref = _sharp_block()
        dx, dy = mfsr.phase_correlation_shift(ref, ref)
        assert (round(dx), round(dy)) == (0, 0)


class TestStackBurst:
    def test_requires_two_frames(self):
        with pytest.raises(ValueError):
            mfsr.stack_burst(np.zeros((1, 8, 8)))

    def test_stack_returns_used_frames(self):
        frames = np.stack([_sharp_block(seed=i) for i in range(5)])
        res = mfsr.stack_burst(frames, sharpness_reject_ratio=0.0)
        assert res.frames_used == 5
        assert res.frames_rejected == 0
        assert res.stacked_shape == (64, 64)

    def test_blurred_frame_rejected(self):
        sharp_frames = [_sharp_block(seed=i) for i in range(4)]
        blurred = np.full((64, 64), 128.0)  # no edges -> zero Laplacian variance
        frames = np.stack(sharp_frames + [blurred])
        res = mfsr.stack_burst(frames, sharpness_reject_ratio=0.5)
        assert res.frames_rejected == 1
        assert res.frames_used == 4

    def test_aligned_stack_sharper_or_equal(self):
        # Frames are shifted copies of the same image; stacking after alignment
        # should not be *less* sharp than the mean single-frame sharpness.
        base = _sharp_block()
        frames = np.stack([_shift_int(base, i % 3, 0) for i in range(6)])
        res = mfsr.stack_burst(frames, sharpness_reject_ratio=0.0)
        assert res.stacked_sharpness >= res.mean_frame_sharpness * 0.9

    def test_identical_frames_gain_near_one(self):
        base = _sharp_block(seed=7)
        frames = np.stack([base.copy() for _ in range(4)])
        res = mfsr.stack_burst(frames, sharpness_reject_ratio=0.0)
        # identical frames -> stacking changes nothing -> gain ~ 1
        assert res.sharpness_gain == pytest.approx(1.0, abs=0.05)

    def test_stack_burst_u8_wrapper(self):
        frames = [_sharp_block(seed=i).tolist() for i in range(3)]
        stacked, res = mfsr.stack_burst_u8(frames)
        assert stacked.shape == (64, 64)
        assert res.frames_used == 3
