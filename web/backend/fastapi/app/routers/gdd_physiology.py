"""GDD physiology router."""
from __future__ import annotations

from fastapi import APIRouter

from ..config import get_settings
from ..schemas import GddRequest, GddResponse
from ..services.physics import gdd as gdd_mod

router = APIRouter(prefix="/internal/agronomy", tags=["gdd"])


@router.post("/gdd", response_model=GddResponse)
def compute_gdd(req: GddRequest) -> GddResponse:
    s = get_settings()
    series = gdd_mod.accumulate(
        req.series, t_base=s.dape_tbase_c,
        flowering_start=s.gdd_flowering_start,
        fruiting_start=s.gdd_fruiting_start,
        harvest_start=s.gdd_harvest_start,
        start_cum=req.start_cum,
    )
    cum = series[-1].gdd_cum if series else req.start_cum
    phase = series[-1].phase if series else gdd_mod.phase_for_gdd(cum)
    locked = gdd_mod.is_fortification_window(
        cum, s.gdd_flowering_start, s.gdd_fruiting_start)
    return GddResponse(
        land_id=req.land_id,
        accumulated_gdd=round(cum, 2),
        current_phase="FORTIFICATION_LOCKED" if locked else phase,
        fortification_locked=locked,
        series=[p.__dict__ for p in series],
    )


@router.get("/boiling-point")
def boiling_point(elevation_m: float) -> dict:
    from ..services.physics import calorimetry
    return {"elevation_m": elevation_m, "boiling_point_c": calorimetry.boiling_point(elevation_m)}
