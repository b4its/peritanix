"""Strict tests: yield projection, protocol prescriptions, trace validation."""
from __future__ import annotations

import math

import pytest

from app.services.agronomy import protocols
from app.services.agronomy import yield_projection as yp
from app.services.epidemiology import trace_validation as tv


class TestYieldProjection:
    def test_scale_factor(self):
        res = yp.project_yield(
            counts={"green_dark": 10, "red_blush": 5, "red_full": 5},
            trees_per_bed=100, sampled_trees=10, horizon_days=14)
        assert res.scale_factor == pytest.approx(10.0)
        assert res.total_trees == 100

    def test_day_one_includes_current_ripe(self):
        # 5 ripe sampled * 10 scale = 50 ripe * 15 g = 750 g = 0.75 kg on day 1
        res = yp.project_yield(
            counts={"green_dark": 0, "red_blush": 0, "red_full": 5},
            trees_per_bed=100, sampled_trees=10, horizon_days=14)
        assert res.day_mass_kg[0] == pytest.approx(0.75)

    def test_cumulative_is_monotonic(self):
        res = yp.project_yield(
            counts={"green_dark": 20, "red_blush": 10, "red_full": 5},
            trees_per_bed=200, sampled_trees=20, horizon_days=14)
        cum = res.cumulative_kg
        assert all(cum[i] <= cum[i + 1] + 1e-9 for i in range(len(cum) - 1))

    def test_total_matches_cumulative_end(self):
        res = yp.project_yield(
            counts={"green_dark": 10, "red_blush": 10, "red_full": 10},
            trees_per_bed=100, sampled_trees=10, horizon_days=14)
        assert res.projected_14d_kg == pytest.approx(res.cumulative_kg[-1], abs=1e-6)

    def test_hotter_ripens_faster(self):
        cold = yp.project_yield(
            counts={"green_dark": 30, "red_blush": 0, "red_full": 0},
            trees_per_bed=100, sampled_trees=10, gdd_per_day=5.0)
        hot = yp.project_yield(
            counts={"green_dark": 30, "red_blush": 0, "red_full": 0},
            trees_per_bed=100, sampled_trees=10, gdd_per_day=20.0)
        assert hot.projected_14d_kg > cold.projected_14d_kg

    def test_larger_sample_higher_confidence(self):
        small = yp.project_yield(counts={"red_full": 5}, trees_per_bed=100, sampled_trees=5)
        large = yp.project_yield(counts={"red_full": 5}, trees_per_bed=100, sampled_trees=25)
        assert large.confidence_pct > small.confidence_pct

    def test_invalid_sampling_raises(self):
        with pytest.raises(ValueError):
            yp.project_yield(counts={}, trees_per_bed=100, sampled_trees=0)
        with pytest.raises(ValueError):
            yp.project_yield(counts={}, trees_per_bed=10, sampled_trees=20)

    def test_zero_ripe_then_ripens(self):
        # Nothing ripe now; greens should ripen into picks over the horizon.
        res = yp.project_yield(
            counts={"green_dark": 50, "red_blush": 0, "red_full": 0},
            trees_per_bed=100, sampled_trees=10, gdd_per_day=30.0, horizon_days=14)
        assert res.day_mass_kg[0] == pytest.approx(0.0)
        assert res.projected_14d_kg > 0.0

    def test_transition_matrix_rows_sum_to_one(self):
        tm = yp.transition_matrix(12.0)
        for _src, row in tm.items():
            assert sum(row.values()) == pytest.approx(1.0, abs=1e-9)


class TestProtocols:
    def test_antraknosa_severe_higher_dose(self):
        mild = protocols.prescribe("FUNG_MANKOZEB_AZOXY", severity_pct=10)
        severe = protocols.prescribe("FUNG_MANKOZEB_AZOXY", severity_pct=90)
        assert severe.dose_g_per_l > mild.dose_g_per_l

    def test_dose_within_labelled_range(self):
        for sev in (0, 25, 50, 75, 100):
            p = protocols.prescribe("FUNG_MANKOZEB_AZOXY", severity_pct=sev)
            assert 2.0 - 1e-9 <= p.dose_g_per_l <= 2.5 + 1e-9

    def test_unknown_key_falls_back_to_scout(self):
        p = protocols.prescribe("NOT_A_REAL_CODE")
        assert p.code == "GENERAL_SCOUT"
        assert "konfirmasi" in p.safety.lower() or "scout" in p.safety.lower() or "sampel" in p.safety.lower()

    def test_ralstonia_has_eradication_steps(self):
        p = protocols.prescribe("BACT_SANITIZE_BURN_CROP_ROTATION")
        assert any("bakar" in s.lower() for s in p.steps)

    def test_zero_dose_protocols(self):
        p = protocols.prescribe("BACT_SANITIZE_BURN_CROP_ROTATION")
        assert p.dose_g_per_l == 0.0

    def test_action_line_mentions_dose(self):
        p = protocols.prescribe("FUNG_MANKOZEB_AZOXY", severity_pct=50)
        assert "g/L" in p.action_line

    def test_catalog_non_empty_and_sorted(self):
        cat = protocols.catalog()
        assert len(cat) >= 8
        codes = [c.code for c in cat]
        assert codes == sorted(codes)


class TestTraceValidation:
    def _trace(self, n=60, duration=180.0, moving=True, constant=False):
        samples = []
        for i in range(n):
            t = duration * i / (n - 1)
            if constant:
                gx = gy = gz = 0.0
            elif moving:
                gx = 0.05 * math.sin(i)
                gy = 0.05 * math.cos(i * 1.3)
                gz = 0.02 * math.sin(i * 0.7)
            else:
                gx = 1e-6
                gy = -1e-6
                gz = 1e-6
            samples.append(tv.TraceSample(t=t, gx=gx, gy=gy, gz=gz))
        return samples

    def test_valid_real_immersion(self):
        res = tv.validate_trace(self._trace(moving=True))
        assert res.valid is True
        assert res.duration_s == pytest.approx(180.0, abs=1.0)

    def test_constant_trace_rejected(self):
        res = tv.validate_trace(self._trace(constant=True))
        assert res.valid is False
        assert any("konstan" in r for r in res.reasons)

    def test_still_trace_rejected(self):
        res = tv.validate_trace(self._trace(moving=False))
        assert res.valid is False

    def test_wrong_duration_rejected(self):
        res = tv.validate_trace(self._trace(duration=30.0))
        assert res.valid is False
        assert any("durasi" in r for r in res.reasons)

    def test_too_few_samples(self):
        res = tv.validate_trace(self._trace(n=3))
        assert res.valid is False

    def test_score_in_range(self):
        res = tv.validate_trace(self._trace())
        assert 0.0 <= res.score <= 1.0
