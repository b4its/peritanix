"""Strict numerical unit tests for DAPE deterministic physics.

These tests assert *exact* expected values (within tight tolerances) derived
from first principles, proving the engine is deterministic and correct — not
merely "runs without crashing".
"""
from __future__ import annotations

import math

import pytest

from app.services.physics import gdd as gdd_mod


class TestGdd:
    def test_gdd_day_standard(self):
        # (30 + 20)/2 - 15 = 10
        assert gdd_mod.gdd_day(20.0, 30.0) == pytest.approx(10.0)

    def test_gdd_day_clamped_below_base(self):
        # (12 + 10)/2 - 15 = -4 -> clamped to 0
        assert gdd_mod.gdd_day(10.0, 12.0) == 0.0

    def test_gdd_day_exactly_base(self):
        assert gdd_mod.gdd_day(15.0, 15.0) == 0.0

    def test_phase_thresholds(self):
        assert gdd_mod.phase_for_gdd(100) == "VEGETATIVE"
        assert gdd_mod.phase_for_gdd(350) == "FLOWERING"
        assert gdd_mod.phase_for_gdd(699) == "FLOWERING"
        assert gdd_mod.phase_for_gdd(700) == "FRUITING"
        assert gdd_mod.phase_for_gdd(1050) == "HARVEST"

    def test_accumulate_is_cumulative(self):
        series = [("d1", 20.0, 30.0), ("d2", 20.0, 30.0), ("d3", 10.0, 12.0)]
        out = gdd_mod.accumulate(series)
        assert [p.gdd_cum for p in out] == pytest.approx([10.0, 20.0, 20.0])
        assert out[-1].phase == "VEGETATIVE"

    def test_fortification_window(self):
        assert gdd_mod.is_fortification_window(400) is True
        assert gdd_mod.is_fortification_window(300) is False
        assert gdd_mod.is_fortification_window(700) is False

    def test_tmax_lt_tmin_raises(self):
        with pytest.raises(ValueError):
            gdd_mod.gdd_day(30.0, 20.0)
