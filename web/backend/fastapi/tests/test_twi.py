"""Strict tests for TWI hydrology + D8 flow accumulation."""
from __future__ import annotations

import math

import pytest

from app.services.physics import twi as twi_mod


class TestTwi:
    def test_flat_45_degree(self):
        # tan(45)=1 -> TWI = ln(a)
        assert twi_mod.twi(10.0, math.radians(45.0)) == pytest.approx(math.log(10.0), abs=1e-9)

    def test_known_value(self):
        # a=100, beta=10deg -> ln(100 / tan(10deg))
        expected = math.log(100.0 / math.tan(math.radians(10.0)))
        assert twi_mod.twi_from_degrees(100.0, 10.0) == pytest.approx(expected, abs=1e-9)

    def test_slope_floor_prevents_infinity(self):
        # beta=0 must NOT explode; floor at 0.1 deg
        val = twi_mod.twi(50.0, 0.0)
        assert math.isfinite(val)
        assert val == pytest.approx(math.log(50.0 / math.tan(math.radians(0.1))), abs=1e-6)

    def test_non_positive_area_raises(self):
        with pytest.raises(ValueError):
            twi_mod.twi(0.0, math.radians(10.0))

    def test_monotonic_in_area(self):
        low = twi_mod.twi(10.0, math.radians(10.0))
        high = twi_mod.twi(1000.0, math.radians(10.0))
        assert high > low

    def test_monotonic_in_inverse_slope(self):
        steep = twi_mod.twi(100.0, math.radians(45.0))
        flat = twi_mod.twi(100.0, math.radians(5.0))
        assert flat > steep

    def test_risk_class_bands(self):
        assert twi_mod.drainage_risk_class(11.0) == "CRITICAL"
        assert twi_mod.drainage_risk_class(9.0) == "HIGH"
        assert twi_mod.drainage_risk_class(7.0) == "MODERATE"
        assert twi_mod.drainage_risk_class(4.0) == "LOW"

    def test_bed_height_prescription(self):
        assert twi_mod.required_bed_height_cm(11.0) == 50
        assert twi_mod.required_bed_height_cm(9.0) == 40
        assert twi_mod.required_bed_height_cm(7.0) == 30
        assert twi_mod.required_bed_height_cm(4.0) == 20


class TestFlowAccumulation:
    def test_single_cell(self):
        acc = twi_mod.flow_accumulation([[10.0]])
        assert acc == [[1.0]]

    def test_flat_grid_no_accumulation(self):
        # All equal elevations -> no drops -> every cell stays at 1
        acc = twi_mod.flow_accumulation([[5.0, 5.0], [5.0, 5.0]])
        assert acc == [[1.0, 1.0], [1.0, 1.0]]

    def test_monotonic_slope_accumulates_downstream(self):
        # Elevations decrease left-to-right; water flows right and accumulates.
        dem = [[5.0, 4.0, 3.0, 2.0, 1.0]]
        acc = twi_mod.flow_accumulation(dem)[0]
        # The last cell (lowest) must receive all upstream contributions.
        assert acc[-1] == pytest.approx(5.0)
        assert acc == pytest.approx([1.0, 2.0, 3.0, 4.0, 5.0])

    def test_valley_concentrates_flow_at_global_minimum(self):
        # Grid decreases BOTH down and right, so the global minimum is the
        # top-right cell (0,2)=3. All flow must converge there.
        dem = [
            [5.0, 4.0, 3.0],
            [6.0, 5.0, 4.0],
            [7.0, 6.0, 5.0],
        ]
        acc = twi_mod.flow_accumulation(dem)
        # Global minimum accumulates the most
        assert acc[0][2] == max(max(row) for row in acc)
        assert acc[0][2] >= 5.0
        # Cells that drain nowhere (the minimum itself) only count themselves
        assert acc[2][2] == 1.0

    def test_deterministic_tie_breaking(self):
        # A perfectly flat grid has no drops; result must be all ones and
        # identical across repeated runs (deterministic ordering).
        flat = [[3.0, 3.0, 3.0], [3.0, 3.0, 3.0], [3.0, 3.0, 3.0]]
        a = twi_mod.flow_accumulation(flat)
        b = twi_mod.flow_accumulation(flat)
        assert a == b == [[1.0] * 3 for _ in range(3)]
