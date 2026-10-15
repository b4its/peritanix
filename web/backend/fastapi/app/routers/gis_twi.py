"""GIS / TWI router — DEM crop + flow accumulation + TWI grid."""
from __future__ import annotations

import math

from fastapi import APIRouter, HTTPException

from ..schemas import BBoxRequest, TwiCell, TwiResponse
from ..services.physics import twi as twi_mod
from ..services.physics.twi import flow_accumulation

router = APIRouter(prefix="/internal/gis", tags=["gis"])


def _synthetic_dem(rows: int, cols: int, bbox: BBoxRequest) -> list[list[float]]:
    """Deterministic synthetic DEM when no SRTM tile is available.

    Builds a smooth valley so flow accumulation produces a meaningful TWI field
    for demo / offline operation. Real deployments substitute a cropped SRTM
    GeoTIFF via rasterio (see TODO in services/gis.py).
    """
    dem: list[list[float]] = []
    base = 850.0
    for r in range(rows):
        row = []
        for c in range(cols):
            # Single valley running down the middle (concave -> high TWI)
            x = (c / max(cols - 1, 1)) * 2 - 1
            valley = (1 - abs(x)) ** 2 * 25.0
            slope = (1 - r / max(rows - 1, 1)) * 40.0
            wave = 3.0 * math.sin(r * 0.7) * math.cos(c * 0.5)
            row.append(base + slope + valley + wave)
        dem.append(row)
    return dem


@router.post("/extract-dem", response_model=TwiResponse)
def extract_dem(req: BBoxRequest) -> TwiResponse:
    rows, cols = 24, 24
    dem = _synthetic_dem(rows, cols, req)
    acc = flow_accumulation(dem)

    # Cell size in metres (small raster -> use degree->m approximation)
    dlat = (req.max_lat - req.min_lat) / rows
    cell_m = dlat * 111_320.0

    cells: list[TwiCell] = []
    twi_values: list[float] = []
    for r in range(rows):
        for c in range(cols):
            lon = req.min_lon + (c + 0.5) / cols * (req.max_lon - req.min_lon)
            lat = req.min_lat + (r + 0.5) / rows * (req.max_lat - req.min_lat)
            # local slope from finite differences (neighbours)
            r0, r1 = max(r - 1, 0), min(r + 1, rows - 1)
            c0, c1 = max(c - 1, 0), min(c + 1, cols - 1)
            dz = max(
                abs(dem[r][c] - dem[r0][c]), abs(dem[r][c] - dem[r1][c]),
                abs(dem[r][c] - dem[r][c0]), abs(dem[r][c] - dem[r][c1]),
            )
            beta = math.atan2(dz, cell_m) if cell_m > 0 else math.radians(1)
            a = acc[r][c] * (cell_m ** 2) / max(cell_m, 1e-6)
            try:
                t = twi_mod.twi(a, beta)
            except ValueError:
                t = 0.0
            twi_values.append(t)
            cells.append(TwiCell(
                lon=round(lon, 6), lat=round(lat, 6),
                elevation_m=round(dem[r][c], 2), upslope_area=round(a, 2),
                twi=round(t, 3), risk=twi_mod.drainage_risk_class(t),
                bed_height_cm=twi_mod.required_bed_height_cm(t),
            ))

    # Estimate serialized size (< 500 KB target from the spec)
    approx_bytes = len(cells) * 90
    return TwiResponse(
        land_id=req.land_id, grid_rows=rows, grid_cols=cols, cells=cells,
        mean_twi=round(sum(twi_values) / len(twi_values), 3),
        max_twi=round(max(twi_values), 3),
        max_risk=twi_mod.drainage_risk_class(max(twi_values)),
        cache_kb=round(approx_bytes / 1024.0, 1),
    )
