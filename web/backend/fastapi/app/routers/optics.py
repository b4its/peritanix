"""Multi-modal optics & epidemiology router (burst MFSR, trans-illum, ooze)."""
from __future__ import annotations

import numpy as np
from fastapi import APIRouter, HTTPException

from ..schemas import (
    MfsrRequest,
    MfsrResponse,
    OozeRequest,
    OozeResponse,
    SeverityRequest,
    SeverityResponse,
    TransIllumRequest,
    TransIllumResponse,
)
from ..services.agronomy import severity as severity_mod
from ..services.epidemiology import ooze_optical_flow
from ..services.optics import mfsr_stacker, trans_illumination

router = APIRouter(prefix="/internal/optics", tags=["optics"])


@router.post("/mfsr-stack", response_model=MfsrResponse)
def mfsr_stack(req: MfsrRequest) -> MfsrResponse:
    try:
        _, res = mfsr_stacker.stack_burst_u8(
            req.frames, sharpness_reject_ratio=req.sharpness_reject_ratio)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return MfsrResponse(
        stacked_shape=res.stacked_shape,
        frames_used=res.frames_used,
        frames_rejected=res.frames_rejected,
        shifts=res.shifts,
        sharpness_gain=res.sharpness_gain,
        stacked_sharpness=res.stacked_sharpness,
    )


@router.post("/trans-illumination", response_model=TransIllumResponse)
def trans_illum(req: TransIllumRequest) -> TransIllumResponse:
    img = np.asarray(req.intensity, dtype=np.float64)
    try:
        res = trans_illumination.trans_illumination(img, sigma_k=req.sigma_k)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return TransIllumResponse(
        attenuation_pct=res.attenuation_pct,
        lesion_fraction=res.lesion_fraction,
        mean_od=res.mean_od,
        max_od=res.max_od,
        latent_fungal_suspected=res.latent_fungal_suspected,
    )


@router.post("/ooze-optical-flow", response_model=OozeResponse)
def ooze_flow(req: OozeRequest) -> OozeResponse:
    frames = [np.asarray(f, dtype=np.float64) for f in req.frames]
    try:
        res = ooze_optical_flow.analyse_ooze(frames)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return OozeResponse(
        mean_speed=res.mean_speed,
        coherence=res.coherence,
        directionality=res.directionality,
        streaming_score=res.streaming_score,
        verdict=res.verdict,
        confidence=res.confidence,
    )


@router.post("/severity", response_model=SeverityResponse)
def severity(req: SeverityRequest) -> SeverityResponse:
    """Pixel-wise lesion severity (Opsi 1) — ONNX segmenter with rule fallback."""
    img = np.asarray(req.image, dtype=np.float32)
    if img.ndim != 3 or img.shape[2] != 3:
        raise HTTPException(status_code=422,
                            detail="image must be (H,W,3) RGB")
    img = np.transpose(img, (2, 0, 1))  # (3,H,W)
    mask = np.asarray(req.leaf_mask, dtype=bool) if req.leaf_mask is not None else None
    try:
        res = severity_mod.assess_severity(img, leaf_mask=mask)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    return SeverityResponse(
        severity_pct=res.severity_pct,
        chlorotic_pct=res.chlorotic_pct,
        necrotic_pct=res.necrotic_pct,
        healthy_pct=res.healthy_pct,
        method=res.method,
        uncertain_pct=res.uncertain_pct,
    )
