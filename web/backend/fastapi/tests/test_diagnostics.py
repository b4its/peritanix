"""Strict tests: Archimedes density assay, brine, grading, fertilizer, Bayesian."""
from __future__ import annotations

import pytest

from app.services.physics import archimedes as arch
from app.services.physics import bayesian as bayes
from app.services.physics import fertilizer as fert


class TestArchimedes:
    def test_all_sink(self):
        assert arch.archimedes_sink_ratio(10, 0, 0) == 1.0

    def test_all_float(self):
        assert arch.archimedes_sink_ratio(0, 0, 10) == 0.0

    def test_neutral_half_weight(self):
        # 5 sink, 10 neutral, 5 float -> (5 + 0.5*10)/20 = 10/20 = 0.5
        assert arch.archimedes_sink_ratio(5, 10, 5) == pytest.approx(0.5)

    def test_empty_raises(self):
        with pytest.raises(ValueError):
            arch.archimedes_sink_ratio(0, 0, 0)

    def test_grade_boundaries(self):
        assert arch.grade_from_sink_ratio(0.85) == "A+"
        assert arch.grade_from_sink_ratio(0.80) == "A+"
        assert arch.grade_from_sink_ratio(0.70) == "A"
        assert arch.grade_from_sink_ratio(0.50) == "B"
        assert arch.grade_from_sink_ratio(0.30) == "BS"

    def test_brine_density_increases_with_salt(self):
        low = arch.brine_for_salinity(10, 1000)
        high = arch.brine_for_salinity(200, 1000)
        assert high.relative_density > low.relative_density
        assert low.relative_density >= 1.0

    def test_brine_invalid_inputs(self):
        with pytest.raises(ValueError):
            arch.brine_for_salinity(10, 0)


class TestFertilizer:
    def test_acidic_ph_triggers_dolomite(self):
        plan = fert.prescribe(ph=5.0, cec_meq=15, hst=20)
        assert plan.dolomite_g_per_m2 > 0
        assert "dolomit" in plan.recommendation.lower()

    def test_vegetative_high_npk(self):
        veg = fert.prescribe(ph=6.5, cec_meq=15, hst=20)
        flow = fert.prescribe(ph=6.5, cec_meq=15, hst=45)
        assert veg.npk_16_16_16_g_per_l > flow.npk_16_16_16_g_per_l

    def test_flowering_adds_calcium_no_urea_advice(self):
        plan = fert.prescribe(ph=6.5, cec_meq=15, hst=45)
        assert plan.calcium_g_per_l > 0
        assert "ur" in plan.recommendation.lower()  # mentions avoiding urea/N

    def test_fruiting_kno3_dominant(self):
        plan = fert.prescribe(ph=6.5, cec_meq=15, hst=75)
        assert plan.kno3_g_per_l > plan.npk_16_16_16_g_per_l

    def test_neutral_ph_no_dolomite(self):
        plan = fert.prescribe(ph=6.8, cec_meq=15, hst=20)
        assert plan.dolomite_g_per_m2 == 0


class TestBayesianFusion:
    def test_posteriors_sum_to_one(self):
        ev = bayes.Evidence(
            visual={"A": 0.7, "B": 0.2, "C": 0.1},
            spatial={"A": 1.5, "B": 0.8, "C": 0.5},
            twi={"A": 1.3, "B": 0.9},
        )
        post = bayes.bayesian_fusion(ev)
        assert sum(post.values()) == pytest.approx(1.0, abs=1e-9)

    def test_strong_visual_evidence_wins(self):
        ev = bayes.Evidence(visual={"Ralstonia": 0.9, "Fusarium": 0.1},
                            prior={"Ralstonia": 0.5, "Fusarium": 0.5})
        post = bayes.bayesian_fusion(ev)
        assert post["Ralstonia"] > post["Fusarium"]

    def test_prior_shifts_result(self):
        ev_flat = bayes.Evidence(visual={"A": 0.6, "B": 0.6},
                                 prior={"A": 0.5, "B": 0.5})
        assert bayes.bayesian_fusion(ev_flat)["A"] == pytest.approx(0.5)
        ev_skew = bayes.Evidence(visual={"A": 0.6, "B": 0.6},
                                 prior={"A": 0.9, "B": 0.1})
        assert bayes.bayesian_fusion(ev_skew)["A"] > 0.8

    def test_empty_raises(self):
        with pytest.raises(ValueError):
            bayes.bayesian_fusion(bayes.Evidence())

    def test_confidence_levels(self):
        assert bayes.confidence_level(0.9) == "HIGH"
        assert bayes.confidence_level(0.7) == "MEDIUM"
        assert bayes.confidence_level(0.4) == "LOW"

    def test_twi_likelihood_wet_favours_waterborne(self):
        wet = bayes.twi_likelihood(9.5)
        dry = bayes.twi_likelihood(3.0)
        assert wet["Ralstonia"] > dry["Ralstonia"]
        assert wet["Phytophthora"] > dry["Phytophthora"]


class TestMoransI:
    def test_clustered_points_positive_i(self):
        # Genuine clustering: a tight cluster of HIGH values near (0,0) and a
        # ring of LOW values further away, all within the bandwidth. High values
        # co-located with high values => positive spatial autocorrelation.
        coords = [
            (0.0, 0.0), (0.0001, 0.0), (0.0, 0.0001),       # tight HIGH cluster
            (0.0005, 0.0), (0.0, 0.0005), (0.0005, 0.0005),  # tight LOW cluster
        ]
        values = [10.0, 9.8, 10.2, 1.0, 1.2, 0.8]
        i = bayes.morans_i(values, coords, bandwidth_m=200)
        assert i > 0

    def test_single_point_returns_zero(self):
        assert bayes.morans_i([5.0], [(0.0, 0.0)]) == 0.0

    def test_constant_values_return_zero(self):
        coords = [(0.0, 0.0), (0.0001, 0.0), (0.0, 0.0001)]
        assert bayes.morans_i([5.0, 5.0, 5.0], coords) == 0.0

    def test_pattern_classification(self):
        assert bayes.classify_spatial_pattern(0.4, 0.2, 0.7) == "DRAINAGE_TROUGH_CLUSTER"
        assert bayes.classify_spatial_pattern(0.4, 0.7, 0.2) == "EDGE_WIND_GRADIENT"
        assert bayes.classify_spatial_pattern(0.01, 0.2, 0.2) == "UNIFORM_BLOCK"


class TestSpatialPatternLikelihood:
    def test_edge_pattern_favours_vector_borne(self):
        lik = bayes.spatial_pattern_likelihood("EDGE_WIND_GRADIENT")
        assert lik["Begomovirus"] > lik["Ralstonia"]

    def test_trough_pattern_favours_water_borne(self):
        lik = bayes.spatial_pattern_likelihood("DRAINAGE_TROUGH_CLUSTER")
        assert lik["Ralstonia"] > lik["Begomovirus"]
        assert lik["Phytophthora"] > lik["Antraknosa"]

    def test_uniform_block_favours_deficiency(self):
        lik = bayes.spatial_pattern_likelihood("UNIFORM_BLOCK")
        assert lik["Defisiensi"] > lik["Ralstonia"]

    def test_unknown_pattern_empty(self):
        assert bayes.spatial_pattern_likelihood("NOPE") == {}


class TestEdgeProximity:
    def test_at_edge_max(self):
        assert bayes.edge_proximity_likelihood(0.0) == 1.0

    def test_deep_inside_low(self):
        assert bayes.edge_proximity_likelihood(100.0, edge_band_m=5.0) == pytest.approx(0.3)

    def test_unknown_uninformative(self):
        assert bayes.edge_proximity_likelihood(-1.0) == 1.0

    def test_monotonic_decreasing(self):
        assert bayes.edge_proximity_likelihood(1.0) > bayes.edge_proximity_likelihood(4.0)


class TestWindAlignment:
    def test_aligned_high(self):
        assert bayes.wind_alignment_likelihood(90.0, 90.0) == 1.5

    def test_opposed_low(self):
        assert bayes.wind_alignment_likelihood(270.0, 90.0) == 0.7

    def test_unknown_uninformative(self):
        assert bayes.wind_alignment_likelihood(-1.0, 90.0) == 1.0

    def test_45deg_within_tolerance(self):
        assert bayes.wind_alignment_likelihood(135.0, 90.0, tolerance_deg=60) == 1.5
