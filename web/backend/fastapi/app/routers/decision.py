"""Decision-support router — yield projection, protocols, trace validation."""
from __future__ import annotations

from fastapi import APIRouter, HTTPException

from ..schemas import (
    ProtocolRequest, ProtocolResponse,
    TraceRequest, TraceResponse,
    YieldRequest, YieldResponse,
)
from ..services.agronomy import protocols, yield_projection
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
        raise HTTPException(status_code=422, detail=str(e))
    return YieldResponse(**res.__dict__)


@router.post("/protocol", response_model=ProtocolResponse)
def protocol_ep(req: ProtocolRequest) -> ProtocolResponse:
    res = protocols.prescribe(req.verdict_key, req.severity_pct, req.confidence)
    return ProtocolResponse(**res.__dict__)


@router.get("/protocols")
def protocols_list() -> dict:
    return {"protocols": [p.__dict__ for p in protocols.catalog()]}


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
