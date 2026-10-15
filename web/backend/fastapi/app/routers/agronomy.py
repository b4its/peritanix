"""Agronomy router — post-harvest grading + microclimate EWS."""
from __future__ import annotations

from fastapi import APIRouter, HTTPException

from ..schemas import (
    EwsRequest,
    EwsResponse,
    GradeRequest,
    GradeResponse,
)
from ..services.agronomy import ews_microclimate, grading

router = APIRouter(prefix="/internal/agronomy", tags=["agronomy"])


@router.post("/grade-fruit", response_model=GradeResponse)
def grade_fruit(req: GradeRequest) -> GradeResponse:
    try:
        res = grading.grade_from_measurements(
            req.contour, req.mean_a_channel, req.lesion_area, req.total_area)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return GradeResponse(
        curvature_ratio=round(res.curvature_ratio, 4),
        red_pct=round(res.red_pct, 3),
        defect_pct=round(res.defect_pct, 3),
        grade=res.grade,
        reasons=res.reasons,
    )


@router.post("/ews-microclimate", response_model=EwsResponse)
def ews(req: EwsRequest) -> EwsResponse:
    try:
        res = ews_microclimate.analyse_microclimate(
            req.rh_series, req.t_series, horizon_h=req.horizon_h)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return EwsResponse(**res.__dict__)
