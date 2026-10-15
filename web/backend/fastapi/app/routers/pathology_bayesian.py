"""Bayesian pathology router — 4-pipeline fusion (stub heavy compute for demo)."""
from __future__ import annotations

from fastapi import APIRouter

from ..schemas import BayesianDiagnosisRequest, BayesianDiagnosisResponse
from ..services.physics import bayesian

router = APIRouter(prefix="/internal/ml", tags=["pathology"])

# Disease -> recommended protocol code (actionable prescription)
_PROTOCOL = {
    "Antraknosa": "FUNG_MANKOZEB_AZOXY",
    "Ralstonia": "BACT_SANITIZE_BURN_CROP_ROTATION",
    "Fusarium": "FUNG_TRICHODERMA_ROTATION",
    "Phytophthora": "FUNG_METALAXYL_DRAINAGE",
    "Keriting_Thrips": "INSEK_ABAMEKTIN",
    "Keriting_Mites": "AKARISIDA_ABAMEKTIN",
    "Begomovirus": "VECTOR_CONTROL_WHITEFLY",
    "Defisiensi": "NUTRI_FOLIAR_BALANCE",
}


@router.post("/bayesian-diagnosis", response_model=BayesianDiagnosisResponse)
def bayesian_diagnosis(req: BayesianDiagnosisRequest) -> BayesianDiagnosisResponse:
    # Assemble evidence.
    spatial = dict(req.spatial)
    twi_lik = bayesian.twi_likelihood(req.local_twi) if req.local_twi is not None else {}

    # Enrich the spatial channel from the PostGIS-passed context: pattern
    # classification, edge proximity and wind alignment.
    sc = req.spatial_context or {}
    pattern = sc.get("pattern")
    if pattern:
        for disease, mult in bayesian.spatial_pattern_likelihood(pattern).items():
            spatial[disease] = spatial.get(disease, 1.0) * mult
    if (dist_edge := sc.get("distance_to_edge_m")) is not None:
        edge_mult = bayesian.edge_proximity_likelihood(dist_edge)
        # Edge proximity only informs airborne/vector diseases.
        for d in ("Begomovirus", "Keriting_Thrips", "Keriting_Mites", "Antraknosa"):
            spatial[d] = spatial.get(d, 1.0) * edge_mult
    if (bearing := sc.get("bearing_deg")) is not None and \
       (wind := sc.get("prevailing_wind_deg")) is not None:
        wind_mult = bayesian.wind_alignment_likelihood(bearing, wind)
        for d in ("Begomovirus", "Keriting_Thrips", "Keriting_Mites", "Antraknosa"):
            spatial[d] = spatial.get(d, 1.0) * wind_mult

    # Trans-illumination attenuation strengthens fungal (Antraknosa) evidence.
    visual = dict(req.visual)
    if req.trans_illumination_attenuation_pct is not None:
        att = max(0.0, min(1.0, req.trans_illumination_attenuation_pct / 100.0))
        visual["Antraknosa"] = visual.get("Antraknosa", 0.3) * (1.0 + att)

    ev = bayesian.Evidence(visual=visual, spatial=spatial,
                           twi=twi_lik, prior=req.prior)
    posterior = bayesian.bayesian_fusion(ev)

    top = max(posterior.items(), key=lambda kv: kv[1])
    verdict = top[0]
    confidence = bayesian.confidence_level(top[1])
    protocol = _PROTOCOL.get(verdict, "GENERAL_SCOUT")

    physics_metrics = {
        "local_twi": req.local_twi,
        "trans_illumination_attenuation_pct": req.trans_illumination_attenuation_pct,
        "ooze_optical_flow": req.ooze_optical_flow,
        "severity_pct": req.severity_pct,
        "spatial_pattern": pattern,
        "distance_to_edge_m": sc.get("distance_to_edge_m"),
        "bearing_deg": sc.get("bearing_deg"),
        "prevailing_wind_deg": sc.get("prevailing_wind_deg"),
        "nearby_infected": sc.get("nearby_infected"),
        "evidence_channels": {
            "visual": bool(visual), "spatial": bool(spatial),
            "twi": bool(twi_lik), "prior": bool(req.prior),
        },
    }

    return BayesianDiagnosisResponse(
        posterior_probabilities={k: round(v, 4) for k, v in posterior.items()},
        final_verdict=verdict,
        confidence_level=confidence,
        recommended_protocol_code=protocol,
        physics_metrics=physics_metrics,
    )
