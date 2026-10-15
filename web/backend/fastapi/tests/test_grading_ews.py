"""Strict tests: post-harvest grading (curvature/colour/defect) + EWS."""
from __future__ import annotations

import pytest

from app.services.agronomy import ews_microclimate as ews
from app.services.agronomy import grading


def _straight_contour() -> list[tuple[float, float]]:
    # A long thin rectangle -> straight spine -> curvature ratio near 1.
    pts = []
    for x in range(0, 100, 5):
        pts.append((float(x), 0.0))
        pts.append((float(x), 10.0))
    return pts


def _curved_contour() -> list[tuple[float, float]]:
    # A banana shape: perpendicular offset grows toward the middle.
    pts = []
    for x in range(0, 100, 5):
        bend = 25.0 * (1 - abs((x - 50) / 50))
        pts.append((float(x), bend))
        pts.append((float(x), bend + 10.0))
    return pts


class TestCurvatureRatio:
    def test_straight_ratio_near_one(self):
        r = grading.curvature_ratio(_straight_contour())
        assert r == pytest.approx(1.0, abs=0.1)

    def test_curved_ratio_greater(self):
        straight = grading.curvature_ratio(_straight_contour())
        curved = grading.curvature_ratio(_curved_contour())
        assert curved > straight

    def test_too_few_points_raises(self):
        with pytest.raises(ValueError):
            grading.curvature_ratio([(0, 0), (1, 1)])


class TestColourAndDefect:
    def test_red_ramp(self):
        assert grading.red_percentage(25.0, 100) == pytest.approx(0.0)
        assert grading.red_percentage(45.0, 100) == pytest.approx(100.0)
        assert grading.red_percentage(35.0, 100) == pytest.approx(50.0)

    def test_red_clamped(self):
        assert grading.red_percentage(100.0, 100) == 100.0
        assert grading.red_percentage(0.0, 100) == 0.0

    def test_defect_percentage(self):
        assert grading.defect_percentage(10.0, 100.0) == pytest.approx(10.0)
        assert grading.defect_percentage(0.0, 100.0) == 0.0

    def test_defect_zero_area_raises(self):
        with pytest.raises(ValueError):
            grading.defect_percentage(1.0, 0.0)


class TestGradeTable:
    def test_prime_fruit(self):
        g = grading.grade_fruit(curv=1.05, red_pct=95.0, defect_pct=1.0)
        assert g.grade == "A+"

    def test_high_defect_forces_bs(self):
        g = grading.grade_fruit(curv=1.05, red_pct=95.0, defect_pct=20.0)
        assert g.grade == "BS"

    def test_mid_ripe_is_b(self):
        g = grading.grade_fruit(curv=1.3, red_pct=60.0, defect_pct=6.0)
        assert g.grade == "B"

    def test_unripe_is_bs(self):
        g = grading.grade_fruit(curv=1.5, red_pct=20.0, defect_pct=2.0)
        assert g.grade == "BS"

    def test_grade_from_measurements_pipeline(self):
        res = grading.grade_from_measurements(
            contour=_straight_contour(), mean_a_channel=45.0,
            lesion_area=1.0, total_area=100.0)
        assert res.grade in ("A+", "A", "B", "BS")
        assert res.reasons


class TestEws:
    def test_hourly_risk_high_when_humid_warm(self):
        # RH 90, T 26 => both memberships high
        assert ews.hourly_risk(90.0, 26.0) > 0.6

    def test_hourly_risk_low_when_dry(self):
        assert ews.hourly_risk(50.0, 26.0) == pytest.approx(0.0, abs=1e-9)

    def test_hourly_risk_low_when_cold(self):
        assert ews.hourly_risk(95.0, 10.0) == pytest.approx(0.0, abs=1e-9)

    def test_requires_series(self):
        with pytest.raises(ValueError):
            ews.analyse_microclimate([80.0, 85.0], [25.0, 26.0])  # too short

    def test_mismatched_lengths_raise(self):
        with pytest.raises(ValueError):
            ews.analyse_microclimate([80, 85, 90], [25, 26])

    def test_high_humidity_triggers_high_risk(self):
        rh = [88, 90, 92, 94, 93, 91]
        t = [25, 26, 27, 26, 25, 26]
        res = ews.analyse_microclimate(rh, t)
        assert res.peak_risk > 0.5
        assert res.irrigation_minutes_per_bed <= 10

    def test_dry_series_low_risk(self):
        rh = [50, 48, 52, 49, 51, 50]
        t = [30, 31, 32, 30, 31, 32]
        res = ews.analyse_microclimate(rh, t)
        assert res.peak_risk < 0.4
        assert res.irrigation_minutes_per_bed == 20

    def test_peak_offset_within_horizon(self):
        rh = [85, 88, 91, 93, 95, 96]
        t = [25, 26, 27, 28, 28, 29]
        res = ews.analyse_microclimate(rh, t, horizon_h=72)
        assert 0 <= res.peak_hour_offset <= 72
