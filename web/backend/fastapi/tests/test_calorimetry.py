"""Strict tests: Black's Law calorimetry (elevation-aware sterilization)."""
from __future__ import annotations

import pytest

from app.services.physics import calorimetry as calo


class TestBoilingPoint:
    def test_sea_level(self):
        assert calo.boiling_point(0.0) == pytest.approx(100.0)

    def test_1000m(self):
        # 100 - 0.0033*1000 = 96.7
        assert calo.boiling_point(1000.0) == pytest.approx(96.7, abs=1e-9)

    def test_monotonic_decrease(self):
        assert calo.boiling_point(2000.0) < calo.boiling_point(100.0)


class TestHotWaterRatio:
    def test_ratio_hits_target_lowland(self):
        r = calo.hot_water_ratio(elevation_m=100.0, well_temp_c=27.0,
                                 target_c=52.0, scale=9.0)
        # Verify via forward model using the EXACT (unrounded) parts
        t = calo.mix_temperature(100.0, r.v_boil_exact, 27.0, r.v_well_exact)
        assert t == pytest.approx(52.0, abs=1e-9)

    def test_ratio_hits_target_highland(self):
        r = calo.hot_water_ratio(elevation_m=1000.0, well_temp_c=22.0,
                                 target_c=52.0, scale=9.0)
        t = calo.mix_temperature(1000.0, r.v_boil_exact, 22.0, r.v_well_exact)
        assert t == pytest.approx(52.0, abs=1e-9)
        # Higher elevation -> lower boiling point -> need MORE boiling water
        r_low = calo.hot_water_ratio(100.0, 22.0, 52.0, 9.0)
        assert r.v_boil_parts > r_low.v_boil_parts

    def test_rounded_ratio_still_within_half_degree(self):
        # The DISPLAYED (rounded) ratio should still land within a physically
        # negligible error of the target (half a degree is far below the
        # accuracy of hand-measured gayung volumes).
        r = calo.hot_water_ratio(850.0, 26.0, 52.0, 9.0)
        t = calo.mix_temperature(850.0, r.v_boil_parts, 26.0, r.v_well_parts)
        assert t == pytest.approx(52.0, abs=0.5)

    def test_known_ratio_value(self):
        # Elevation 0 (boil=100), well=27, target=52, scale=9:
        # V_boil = 9 * (52-27)/(100-52) = 9*25/48 = 4.6875
        r = calo.hot_water_ratio(0.0, 27.0, 52.0, 9.0)
        assert r.v_boil_parts == pytest.approx(4.69, abs=0.01)

    def test_unreachable_target_raises_well_too_hot(self):
        with pytest.raises(ValueError):
            calo.hot_water_ratio(100.0, well_temp_c=60.0, target_c=52.0)

    def test_unreachable_target_boil_too_low(self):
        # At 15000 m, boiling ~50.5 C < target 52 -> impossible
        with pytest.raises(ValueError):
            calo.hot_water_ratio(15000.0, well_temp_c=20.0, target_c=52.0)

    def test_ratio_label_mentions_gayung(self):
        r = calo.hot_water_ratio(850.0, 26.0)
        assert "gayung" in r.ratio_label


class TestMixTemperature:
    def test_equal_volumes(self):
        # 50/50 mix of 100 and 0 -> 50
        assert calo.mix_temperature(0.0, 1.0, 0.0, 1.0) == pytest.approx(50.0)

    def test_zero_total_raises(self):
        with pytest.raises(ValueError):
            calo.mix_temperature(0.0, 0.0, 20.0, 0.0)
