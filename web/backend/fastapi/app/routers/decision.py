"""Decision-support router — yield projection, protocols, trace validation."""
from __future__ import annotations

from fastapi import APIRouter, HTTPException

from ..schemas import (
    EconomicsRequest,
    EconomicsResponse,
    HarvestDecisionRequest,
    HarvestDecisionResponse,
    ProtocolRequest,
    ProtocolResponse,
    ProtocolRulesRequest,
    ProtocolRulesResponse,
    TraceRequest,
    TraceResponse,
    TriggerOut,
    YieldRequest,
    YieldResponse,
)
from ..services.agronomy import (
    economics,
    harvest_decision,
    protocol_rules,
    protocols,
    yield_projection,
)
from ..services.epidemiology import trace_validation

router = APIRouter(prefix="/internal/decision", tags=["decision"])


@router.post("/yield-projection", response_model=YieldResponse)
def yield_projection_ep(req: YieldRequest) -> YieldResponse:
    try:
        res = yield_projection.project_yield(
            counts={"green_dark": req.green_dark, "red_blush": req.red_blush,
                    "red_full": req.red_full},
            trees_per_bed=req.trees_per_bed,
            sampled_trees=req.sampled_trees,
            horizon_days=req.horizon_days,
            gdd_per_day=req.gdd_per_day,
            weights=yield_projection.YieldWeights(
                green_dark_g=req.green_dark_g, red_blush_g=req.red_blush_g,
                red_full_g=req.red_full_g),
        )
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return YieldResponse(**res.__dict__)


@router.post("/protocol", response_model=ProtocolResponse)
def protocol_ep(req: ProtocolRequest) -> ProtocolResponse:
    res = protocols.prescribe(req.verdict_key, req.severity_pct, req.confidence)
    return ProtocolResponse(**res.__dict__)


@router.post("/harvest-decision", response_model=HarvestDecisionResponse)
def harvest_decision_ep(req: HarvestDecisionRequest) -> HarvestDecisionResponse:
    inp = harvest_decision.HarvestInputs(
        accumulated_gdd=req.accumulated_gdd,
        current_phase=req.current_phase,
        archimedes_sink_ratio=req.archimedes_sink_ratio,
        curvature_ratio=req.curvature_ratio,
        red_pct=req.red_pct,
        defect_pct=req.defect_pct,
        price_trend_14d=req.price_trend_14d,
    )
    d = harvest_decision.decision_from_gdd_phase(req.accumulated_gdd, inp)
    return HarvestDecisionResponse(**d.__dict__)


@router.get("/protocols")
def protocols_list() -> dict:
    return {"protocols": [p.__dict__ for p in protocols.catalog()]}


@router.post("/protocol-rules", response_model=ProtocolRulesResponse)
def protocol_rules_ep(req: ProtocolRulesRequest) -> ProtocolRulesResponse:
    """Evaluate field-workflow triggers (e.g. mandatory ooze test on wilting+wet)."""
    trigs = protocol_rules.evaluate_protocol_rules(
        req.symptoms, req.local_twi, req.already_recorded_ooze)
    return ProtocolRulesResponse(
        triggers=[TriggerOut(**t.__dict__) for t in trigs],
        blocked=any(t.blocking for t in trigs),
    )


@router.post("/economics", response_model=EconomicsResponse)
def economics_ep(req: EconomicsRequest) -> EconomicsResponse:
    try:
        res = economics.analyse(economics.EconomicsInputs(
            area_ha=req.area_ha, baseline_yield_t_ha=req.baseline_yield_t_ha,
            price_per_kg=req.price_per_kg,
            fertilizer_cost_per_ha_idr=req.fertilizer_cost_per_ha_idr,
            fertilizer_saving_pct=req.fertilizer_saving_pct,
            yield_uplift_pct=req.yield_uplift_pct,
            outbreak_probability=req.outbreak_probability,
            loss_if_late_pct=req.loss_if_late_pct,
            loss_if_early_pct=req.loss_if_early_pct,
        ))
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return EconomicsResponse(**res.__dict__)


@router.post("/validate-trace", response_model=TraceResponse)
def validate_trace_ep(req: TraceRequest) -> TraceResponse:
    samples = [
        trace_validation.TraceSample(
            t=s.t, gx=s.gx, gy=s.gy, gz=s.gz, ax=s.ax, ay=s.ay, az=s.az)
        for s in req.samples
    ]
    res = trace_validation.validate_trace(
        samples, target_s=req.target_s, duration_tol_s=req.duration_tol_s,
        min_motion_variance=req.min_motion_variance)
    return TraceResponse(**res.__dict__)
