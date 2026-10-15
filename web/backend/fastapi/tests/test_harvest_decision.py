"""Strict tests: Harvest Decision Engine (post-harvest action fusion)."""
from __future__ import annotations

import pytest

from app.services.agronomy import harvest_decision as hd


class TestQualityScore:
    def test_perfect_is_one(self):
        inp = hd.HarvestInputs(accumulated_gdd=1100, archimedes_sink_ratio=1.0,
                               curvature_ratio=1.0, red_pct=100, defect_pct=0)
        assert hd.quality_score(inp) == pytest.approx(1.0, abs=1e-6)

    def test_worst_is_low(self):
        inp = hd.HarvestInputs(accumulated_gdd=1100, archimedes_sink_ratio=0.0,
                               curvature_ratio=1.5, red_pct=0, defect_pct=100)
        assert hd.quality_score(inp) < 0.1

    def test_monotonic_in_sink_ratio(self):
        lo = hd.HarvestInputs(1000, archimedes_sink_ratio=0.3)
        hi = hd.HarvestInputs(1000, archimedes_sink_ratio=0.9)
        assert hd.quality_score(hi) > hd.quality_score(lo)

    def test_bounded(self):
        for sink in (0, 0.5, 1):
            for red in (0, 50, 100):
                for defect in (0, 50, 100):
                    q = hd.quality_score(hd.HarvestInputs(
                        1000, archimedes_sink_ratio=sink, red_pct=red, defect_pct=defect))
                    assert 0.0 <= q <= 1.0


class TestGradeMirror:
    def test_matches_grading_table(self):
        assert hd._grade(0.9, 1.05, 95, 1) == "A+"
        assert hd._grade(0.7, 1.2, 80, 4) == "A"
        assert hd._grade(0.5, 1.4, 60, 8) == "B"
        assert hd._grade(0.9, 1.0, 95, 20) == "BS"  # defects disqualify first

    def test_immature_is_bs(self):
        assert hd._grade(0.9, 1.0, 30, 1) == "BS"


class TestDecide:
    def test_high_defect_forces_sort(self):
        inp = hd.HarvestInputs(1100, archimedes_sink_ratio=0.9, red_pct=95, defect_pct=20)
        d = hd.decide(inp)
        assert d.recommended_action == "PROCESS_OR_SORT"
        assert d.spoilage_risk == "HIGH"

    def test_immature_holds(self):
        inp = hd.HarvestInputs(1000, archimedes_sink_ratio=0.8, red_pct=30, defect_pct=1)
        d = hd.decide(inp)
        assert d.recommended_action == "HOLD_14D"

    def test_prime_sells_now(self):
        inp = hd.HarvestInputs(1100, archimedes_sink_ratio=0.85, red_pct=95,
                               curvature_ratio=1.05, defect_pct=1)
        d = hd.decide(inp)
        assert d.recommended_action == "SELL_NOW"
        assert d.expected_grade == "A+"

    def test_market_uptrend_holds_good_lot(self):
        inp = hd.HarvestInputs(1100, archimedes_sink_ratio=0.8, red_pct=85,
                               curvature_ratio=1.1, defect_pct=2, price_trend_14d=0.2)
        d = hd.decide(inp)
        assert d.recommended_action == "HOLD_14D"
        assert any("harga" in r.lower() for r in d.rationale)

    def test_uptrend_does_not_hold_bs(self):
        # Low ripeness => BS grade; a price gamble must not delay a poor lot.
        inp = hd.HarvestInputs(1000, archimedes_sink_ratio=0.3, red_pct=40,
                               defect_pct=1, price_trend_14d=0.3)
        d = hd.decide(inp)
        assert d.expected_grade == "BS"
        assert d.recommended_action == "HOLD_14D"  # BS + immature -> ripen first
        # But with a defect-heavy BS lot, it must SELL/SORT, never gamble.
        inp2 = hd.HarvestInputs(1000, archimedes_sink_ratio=0.3, red_pct=60,
                                defect_pct=12, price_trend_14d=0.3)
        d2 = hd.decide(inp2)
        assert d2.recommended_action in ("SELL_NOW", "PROCESS_OR_SORT")


class TestGddPhaseGate:
    def test_vegetative_blocks_sell(self):
        inp = hd.HarvestInputs(200, current_phase="VEGETATIVE",
                               archimedes_sink_ratio=0.85, red_pct=95, defect_pct=1)
        d = hd.decision_from_gdd_phase(200, inp)
        assert d.recommended_action == "HOLD_14D"
        assert any("termal" in r.lower() for r in d.rationale)

    def test_harvest_phase_allows_sell(self):
        inp = hd.HarvestInputs(1150, current_phase="HARVEST",
                               archimedes_sink_ratio=0.85, red_pct=95,
                               curvature_ratio=1.05, defect_pct=1)
        d = hd.decision_from_gdd_phase(1150, inp)
        assert d.recommended_action == "SELL_NOW"

    def test_rationale_includes_gdd(self):
        inp = hd.HarvestInputs(900, current_phase="FRUITING", red_pct=60, defect_pct=2)
        d = hd.decision_from_gdd_phase(900, inp)
        assert any("GDD" in r for r in d.rationale)
