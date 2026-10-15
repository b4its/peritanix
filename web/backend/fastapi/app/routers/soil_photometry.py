"""Soil router — Stokes texture + dark-chamber photometry + fertilizer plan."""
from __future__ import annotations

from fastapi import APIRouter

from ..schemas import (
    FertilizerRequest, FertilizerResponse,
    PhotometryRequest, PhotometryResponse,
    SoilCalibrateRequest, SoilCalibrateResponse,
)
from ..services.physics import colorimetry
from ..services.physics import fertilizer as fert_mod
from ..services.physics import stokes as stokes_mod

router = APIRouter(prefix="/internal/physics", tags=["soil"])


@router.post("/soil-calibrate", response_model=SoilCalibrateResponse)
def soil_calibrate(req: SoilCalibrateRequest) -> SoilCalibrateResponse:
    res = stokes_mod.classify_from_layers(
        req.layers.sand_thickness, req.layers.silt_thickness, req.layers.clay_thickness,
    )
    notes = {
        "sand": "Tanah berpasir: drainase cepat, pupuk mudah tercuci — kocor lebih sering.",
        "loamy sand": "Tanah berpasir: kocor bertahap, tambah bahan organik.",
        "sandy loam": "Tanah ringan: pemupukan bertahap, jaga kelembaban.",
        "loam": "Tanah ideal: manajemen standar.",
        "silt loam": "Tanah liat-debu: jaga aerasi, hindari genangan.",
        "clay loam": "Tanah agak berat: bedengan tinggi, drainase baik.",
        "clay": "Tanah liat berat: KTK tinggi, bedengan 40-50 cm wajib.",
    }.get(res.soil_class, "Manajemen standar.")
    return SoilCalibrateResponse(
        land_id=req.land_id,
        sand_pct=res.sand_pct, silt_pct=res.silt_pct, clay_pct=res.clay_pct,
        soil_class=res.soil_class, cec_meq=res.cec_meq,
        fertilizer_base_note=notes,
    )


@router.post("/photometry", response_model=PhotometryResponse)
def photometry(req: PhotometryRequest) -> PhotometryResponse:
    corrected = colorimetry.apply_white_reference(req.measured_rgb, req.white_ref_rgb)
    lab = colorimetry.rgb_to_lab(*corrected)
    matched = None
    if req.curve:
        curve = [(c["value"], tuple(c["lab"])) for c in req.curve]
        matched = round(colorimetry.match_reagent_curve(lab, curve), 3)
    return PhotometryResponse(
        corrected_rgb=(round(corrected[0], 2), round(corrected[1], 2), round(corrected[2], 2)),
        lab=(round(lab[0], 2), round(lab[1], 2), round(lab[2], 2)),
        matched_value=matched,
        nanometres_note="White-reference gain applied per channel to cancel LED flash bias.",
    )


@router.post("/fertilizer-plan", response_model=FertilizerResponse)
def fertilizer_plan(req: FertilizerRequest) -> FertilizerResponse:
    plan = fert_mod.prescribe(req.ph, req.cec_meq, req.hst,
                              req.target_yield_t_ha, req.soil_class)
    return FertilizerResponse(
        npk_16_16_16_g_per_l=plan.npk_16_16_16_g_per_l,
        kno3_g_per_l=plan.kno3_g_per_l,
        calcium_g_per_l=plan.calcium_g_per_l,
        recommendation=plan.recommendation,
    )
