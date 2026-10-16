"""Tests for deterministic fruit counting (Opsi 3 yield estimation core).

The counter segments a canopy photo into three ripeness classes
(green_dark / red_blush / red_full) and counts fruit blobs. It is the
deterministic, auditable core the eventual YOLO detector is seeded from, and it
must feed `yield_projection` with sane counts.
"""
from __future__ import annotations

import numpy as np
import pytest

from app.services.agronomy import fruit_counting as fc
from app.services.agronomy import yield_projection as yp

H = W = 200


def _canopy(fruits, foliage=(0.30, 0.55, 0.25)):
    img = np.zeros((H, W, 3), dtype=np.float64)
    img[..., 0], img[..., 1], img[..., 2] = foliage
    yy, xx = np.mgrid[0:H, 0:W]
    for cy, cx, r, col in fruits:
        m = (yy - cy) ** 2 + (xx - cx) ** 2 <= r * r
        img[m] = col
    return img


RED = (0.85, 0.10, 0.10)
BLUSH = (0.90, 0.45, 0.10)
GREEN = (0.12, 0.40, 0.12)


class TestClassification:
    def test_rgb_to_hsv_primaries(self):
        img = np.array([[[1, 0, 0], [0, 1, 0], [0, 0, 1]]], dtype=np.float64)
        hsv = fc._rgb_to_hsv(img)[0]
        assert hsv[0][0] == pytest.approx(0.0, abs=1e-6)      # red hue 0
        assert hsv[1][0] == pytest.approx(1 / 3, abs=1e-6)    # green hue 1/3
        assert hsv[2][0] == pytest.approx(2 / 3, abs=1e-6)    # blue hue 2/3
        assert hsv[0][1] == pytest.approx(1.0, abs=1e-6)      # full saturation

    def test_input_validation(self):
        with pytest.raises(ValueError):
            fc.classify_pixels(np.zeros((10, 10), dtype=np.float64))

    def test_accepts_0_255_range(self):
        img01 = _canopy([(40, 40, 10, RED)])
        img255 = img01 * 255.0
        r1 = fc.count_fruit(img01)
        r2 = fc.count_fruit(img255)
        assert r1.red_full == r2.red_full == 1


class TestCounting:
    def test_counts_each_class(self):
        img = _canopy([
            (40, 40, 10, RED), (40, 120, 9, RED),   # 2 ripe
            (120, 80, 8, BLUSH),                    # 1 blush
            (150, 150, 9, GREEN),                   # 1 dark green
        ])
        res = fc.count_fruit(img)
        assert res.red_full == 2
        assert res.red_blush == 1
        assert res.green_dark == 1
        assert res.total == 4

    def test_empty_canopy_is_zero(self):
        res = fc.count_fruit(_canopy([]))
        assert res.total == 0
        assert res.red_full == res.red_blush == res.green_dark == 0

    def test_speckle_noise_rejected(self):
        # Many 1-px red specks must not inflate the count (min-area filter).
        img = _canopy([])
        rng = np.random.default_rng(0)
        for _ in range(200):
            y, x = rng.integers(0, H), rng.integers(0, W)
            img[y, x] = RED
        res = fc.count_fruit(img, min_area_px=12)
        assert res.red_full == 0

    def test_more_fruit_more_count(self):
        one = fc.count_fruit(_canopy([(50, 50, 10, RED)])).red_full
        three = fc.count_fruit(_canopy(
            [(50, 50, 10, RED), (50, 120, 10, RED), (120, 90, 10, RED)])).red_full
        assert one == 1 and three == 3

    def test_mean_blob_area_reported(self):
        res = fc.count_fruit(_canopy([(40, 40, 10, RED)]))
        assert res.mean_blob_area_px["red_full"] > 0.0
        assert res.image_size == (H, W)


class TestFeedsYieldProjection:
    def test_counts_drive_projection(self):
        img = _canopy([
            (40, 40, 10, RED), (40, 120, 10, RED),      # ripe now
            (120, 80, 9, BLUSH),
            (150, 60, 9, GREEN), (150, 150, 9, GREEN),
        ])
        res = fc.count_fruit(img)
        proj = yp.project_yield(
            counts={"green_dark": res.green_dark, "red_blush": res.red_blush,
                    "red_full": res.red_full},
            trees_per_bed=100, sampled_trees=10, horizon_days=14)
        # 2 ripe fruit × 15 g × scale 10 = 300 g = 0.3 kg on day 1.
        assert proj.day_mass_kg[0] == pytest.approx(0.3, abs=1e-6)
        assert proj.projected_14d_kg > proj.day_mass_kg[0]
        assert proj.scale_factor == pytest.approx(10.0, abs=1e-6)
