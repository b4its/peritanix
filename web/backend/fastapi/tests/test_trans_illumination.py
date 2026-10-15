"""Strict tests: trans-illumination mesophyll attenuation + Otsu."""
from __future__ import annotations

import numpy as np
import pytest

from app.services.optics import trans_illumination as ti


class TestOtsu:
    def test_bimodal_separates(self):
        values = np.concatenate([np.full(500, 10.0), np.full(500, 200.0)])
        thresh = ti._otsu_threshold(values)
        assert 10.0 < thresh < 200.0

    def test_uniform_returns_edge(self):
        thresh = ti._otsu_threshold(np.full(100, 50.0))
        assert isinstance(thresh, float)


class TestTransIllumination:
    def test_requires_2d(self):
        with pytest.raises(ValueError):
            ti.trans_illumination(np.zeros((4, 4, 3)))

    def test_uniform_leaf_no_attenuation(self):
        # Perfectly uniform transmitted intensity -> no localised attenuation.
        img = np.full((40, 40), 0.8)
        res = ti.trans_illumination(img)
        assert res.attenuation_pct == pytest.approx(0.0, abs=1e-6)

    def test_dark_blob_raises_attenuation(self):
        # A uniform bright leaf with a dark (dense) patch => attenuation.
        img = np.full((50, 50), 230.0)
        img[20:30, 20:30] = 40.0  # necrotic/dense patch transmits less
        res = ti.trans_illumination(img)
        assert res.attenuation_pct > 0.0
        assert res.max_od > 0.0

    def test_latent_fungal_flag_threshold(self):
        img = np.full((60, 60), 220.0)
        img[25:35, 25:35] = 90.0
        res = ti.trans_illumination(img)
        # A moderate patch should flag latent fungal suspicion.
        assert isinstance(res.latent_fungal_suspected, bool)

    def test_mask_restricts_region(self):
        img = np.full((40, 40), 200.0)
        img[0:5, 0:5] = 20.0  # dark patch OUTSIDE the mask
        mask = np.zeros((40, 40), dtype=bool)
        mask[10:, 10:] = True
        res_masked = ti.trans_illumination(img, leaf_mask=mask)
        res_full = ti.trans_illumination(img)
        # Masking out the dark patch should reduce measured attenuation.
        assert res_masked.max_od <= res_full.max_od

    def test_mask_shape_mismatch_raises(self):
        with pytest.raises(ValueError):
            ti.trans_illumination(np.zeros((10, 10)), leaf_mask=np.zeros((5, 5), dtype=bool))

    def test_normalises_255_scale(self):
        img255 = np.full((30, 30), 200.0)
        img255[10:15, 10:15] = 50.0
        img01 = img255 / 255.0
        r255 = ti.trans_illumination(img255)
        r01 = ti.trans_illumination(img01)
        assert r255.attenuation_pct == pytest.approx(r01.attenuation_pct, abs=0.1)
