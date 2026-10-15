"""Strict tests: Stokes sedimentation, USDA classification, CEC."""
from __future__ import annotations

import math

import pytest

from app.services.physics import stokes as st


class TestStokesLaw:
    def test_velocity_positive_and_scales_with_r_squared(self):
        v1 = st.stokes_velocity(1e-5)
        v2 = st.stokes_velocity(2e-5)
        # v ~ r^2 -> doubling r quadruples v
        assert v2 == pytest.approx(4 * v1, rel=1e-9)

    def test_known_quartz_sand_velocity(self):
        # r = 50 um (medium sand) in water: v ~ 2.5 mm/s
        v = st.stokes_velocity(50e-6)
        assert 0.001 < v < 0.01  # sane order of magnitude
        # exact analytic check
        expected = (2 / 9) * (2650.0 - 998.0) * 9.80665 * (50e-6 ** 2) / 1.002e-3
        assert v == pytest.approx(expected, rel=1e-12)

    def test_zero_radius_raises(self):
        with pytest.raises(ValueError):
            st.stokes_velocity(0.0)

    def test_diameter_decreases_with_time(self):
        d40 = st.stokes_diameter_for_time(40.0)
        d7200 = st.stokes_diameter_for_time(7200.0)
        assert d40 > d7200  # bigger particles settle sooner

    def test_diameter_analytic(self):
        # r = sqrt(9*mu*h / (2*(rho_p-rho_f)*g*t)); h=1
        t = 40.0
        r = math.sqrt(9 * 1.002e-3 * 1.0 / (2 * (2650 - 998) * 9.80665 * t))
        assert st.stokes_diameter_for_time(t) == pytest.approx(2 * r, rel=1e-12)


class TestUsdaClassification:
    def test_pure_sand(self):
        assert st.classify_usda(95, 3, 2) == "sand"

    def test_pure_clay(self):
        assert st.classify_usda(5, 10, 85) == "clay"

    def test_loam_center(self):
        # Classic loam ~ 40/40/20
        assert st.classify_usda(40, 40, 20) == "loam"

    def test_silt_loam(self):
        assert st.classify_usda(15, 70, 15) == "silt loam"

    def test_normalisation_of_non_100_inputs(self):
        # Sums to 200 but proportions = sand/clay
        a = st.classify_usda(50, 25, 25)
        b = st.classify_usda(100, 50, 50)
        assert a == b

    def test_zero_sum_raises(self):
        with pytest.raises(ValueError):
            st.classify_usda(0, 0, 0)


class TestCec:
    def test_clay_higher_cec_than_sand(self):
        clay = st.cec_from_texture(5, 10, 85)
        sand = st.cec_from_texture(95, 3, 2)
        assert clay > sand

    def test_classify_from_layers_pipeline(self):
        res = st.classify_from_layers(38.0, 40.0, 22.0)
        assert res.soil_class == "loam"
        assert res.sand_pct == pytest.approx(38.0)
        assert res.silt_pct == pytest.approx(40.0)
        assert res.clay_pct == pytest.approx(22.0)
        assert 10 < res.cec_meq < 25

    def test_layers_zero_raises(self):
        with pytest.raises(ValueError):
            st.classify_from_layers(0, 0, 0)
