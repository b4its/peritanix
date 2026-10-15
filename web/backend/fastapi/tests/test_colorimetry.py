"""Strict tests for CIELAB colorimetry + white-reference correction."""
from __future__ import annotations

import pytest

from app.services.physics import colorimetry as col


class TestColorimetry:
    def test_white_to_lab(self):
        l, a, b = col.rgb_to_lab(255, 255, 255)
        assert l == pytest.approx(100.0, abs=0.1)
        assert a == pytest.approx(0.0, abs=0.1)
        assert b == pytest.approx(0.0, abs=0.1)

    def test_black_to_lab(self):
        l, a, b = col.rgb_to_lab(0, 0, 0)
        assert l == pytest.approx(0.0, abs=1e-6)

    def test_red_channel_positive_a(self):
        _, a, _ = col.rgb_to_lab(255, 0, 0)
        assert a > 0

    def test_blue_channel_negative_b(self):
        _, _, b = col.rgb_to_lab(0, 0, 255)
        assert b < 0

    def test_white_reference_identity(self):
        ref = (255.0, 255.0, 255.0)
        out = col.apply_white_reference((100, 150, 200), ref)
        assert out == pytest.approx((100, 150, 200), abs=1e-6)

    def test_white_reference_normalises_flash_bias(self):
        # A dimmer flash captures lower values; correction should lift them.
        captured = (180.0, 175.0, 170.0)
        white = (200.0, 200.0, 200.0)
        corrected = col.apply_white_reference(captured, white)
        assert corrected[0] > captured[0]

    def test_white_reference_invalid(self):
        with pytest.raises(ValueError):
            col.apply_white_reference((100, 100, 100), (0, 255, 255))

    def test_delta_e_identical_is_zero(self):
        assert col.delta_e((50, 10, -5), (50, 10, -5)) == 0.0

    def test_delta_e_positive(self):
        assert col.delta_e((50, 10, -5), (60, 12, -3)) > 0

    def test_match_reagent_curve_exact_node(self):
        curve = [(4.0, col.rgb_to_lab(255, 0, 0)),
                 (7.0, col.rgb_to_lab(0, 255, 0)),
                 (10.0, col.rgb_to_lab(0, 0, 255))]
        # Query exactly at the pH 7 node
        matched = col.match_reagent_curve(col.rgb_to_lab(0, 255, 0), curve)
        assert matched == pytest.approx(7.0, abs=0.5)

    def test_match_empty_curve_raises(self):
        with pytest.raises(ValueError):
            col.match_reagent_curve((0, 0, 0), [])
