"""Strict tests: economic impact model."""
from __future__ import annotations

import pytest

from app.services.agronomy import economics as eco


class TestEconomics:
    def test_basic_analysis_runs(self):
        r = eco.analyse(eco.EconomicsInputs(area_ha=0.5))
        assert r.area_ha == 0.5
        assert r.total_benefit_idr > 0
        assert r.net_benefit_idr == r.total_benefit_idr - r.estimated_darif_cost_idr

    def test_larger_area_larger_benefit(self):
        small = eco.analyse(eco.EconomicsInputs(area_ha=0.2))
        large = eco.analyse(eco.EconomicsInputs(area_ha=2.0))
        assert large.total_benefit_idr > small.total_benefit_idr

    def test_zero_area_raises(self):
        with pytest.raises(ValueError):
            eco.analyse(eco.EconomicsInputs(area_ha=0.0))
        with pytest.raises(ValueError):
            eco.analyse(eco.EconomicsInputs(area_ha=-1))

    def test_invalid_pct_raises(self):
        with pytest.raises(ValueError):
            eco.analyse(eco.EconomicsInputs(area_ha=1.0, fertilizer_saving_pct=1.5))

    def test_higher_outbreak_raises_avoided_loss(self):
        low = eco.analyse(eco.EconomicsInputs(area_ha=1.0, outbreak_probability=0.1))
        high = eco.analyse(eco.EconomicsInputs(area_ha=1.0, outbreak_probability=0.8))
        assert high.avoided_loss_idr > low.avoided_loss_idr

    def test_no_fertilizer_saving_reduces_benefit(self):
        savings = eco.analyse(eco.EconomicsInputs(area_ha=1.0, fertilizer_saving_pct=0.3))
        none = eco.analyse(eco.EconomicsInputs(area_ha=1.0, fertilizer_saving_pct=0.0))
        assert savings.total_benefit_idr > none.total_benefit_idr

    def test_roi_positive_for_typical_plot(self):
        # 0.5 ha, typical parameters -> should be strongly positive.
        r = eco.analyse(eco.EconomicsInputs(area_ha=0.5))
        assert r.roi_pct > 0
        assert 0 < r.break_even_fraction < 1

    def test_break_even_fraction_is_cost_over_benefit(self):
        r = eco.analyse(eco.EconomicsInputs(area_ha=1.0))
        assert r.break_even_fraction == pytest.approx(
            r.estimated_darif_cost_idr / r.total_benefit_idr, abs=1e-3)

    def test_notes_present(self):
        r = eco.analyse(eco.EconomicsInputs(area_ha=0.1))
        assert len(r.notes) >= 1
        assert any("kalibrasi" in n.lower() or "asumsi" in n.lower() for n in r.notes)

    def test_tiny_plot_warns(self):
        r = eco.analyse(eco.EconomicsInputs(area_ha=0.15))
        assert any("0.2 ha" in n for n in r.notes)

    def test_high_disease_pressure_note(self):
        r = eco.analyse(eco.EconomicsInputs(area_ha=1.0, outbreak_probability=0.6))
        assert any("penyakit" in n.lower() for n in r.notes)
