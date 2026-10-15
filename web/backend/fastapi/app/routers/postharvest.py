"""Post-harvest router — Black calorimetry + Archimedes assay + grading."""
from __future__ import annotations

from fastapi import APIRouter

from ..schemas import (
    ArchimedesRequest,
    ArchimedesResponse,
    CalorimetryRequest,
    CalorimetryResponse,
)
from ..services.physics import archimedes as arch
from ..services.physics import calorimetry as calo

router = APIRouter(prefix="/internal/postharvest", tags=["postharvest"])


@router.post("/calorimetry", response_model=CalorimetryResponse)
def calorimetry(req: CalorimetryRequest) -> CalorimetryResponse:
    r = calo.hot_water_ratio(req.elevation_m, req.well_temp_c, req.target_c, req.scale)
    tts = (f"Campurkan {r.v_boil_parts:.1f} gayung air mendidih dengan "
           f"{r.v_well_parts:.0f} gayung air sumur. "
           f"Rendam cabai selama 3 menit, lalu angkat dan tiriskan.")
    return CalorimetryResponse(
        boiling_point_c=r.boiling_point_c, target_c=r.target_c,
        v_boil_parts=r.v_boil_parts, v_well_parts=r.v_well_parts,
        ratio_label=r.ratio_label, tts_text=tts, countdown_s=180,
    )


@router.post("/archimedes", response_model=ArchimedesResponse)
def archimedes(req: ArchimedesRequest) -> ArchimedesResponse:
    ratio = arch.archimedes_sink_ratio(req.sink_count, req.neutral_count, req.float_count)
    brine_rho = None
    if req.salt_g > 0 and req.water_ml > 0:
        brine_rho = arch.brine_for_salinity(req.salt_g, req.water_ml).relative_density
    return ArchimedesResponse(
        sink_ratio=round(ratio, 4),
        physics_grade=arch.grade_from_sink_ratio(ratio),
        brine_relative_density=brine_rho,
    )
