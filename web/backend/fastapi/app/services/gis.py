"""DEM extraction & flow accumulation — real rasterio path + synthetic fallback.

TAHAP 1.2 requires cropping an SRTM 30 m DEM to the land's bounding box and
computing the upslope contributing area per cell. This module:

1. **Real path** — if a DEM GeoTIFF is available (a mounted SRTM tile or a URL
   resolved by `DAPE_DEM_PATH`), crop it to the bbox with `rasterio.mask` and
   read the elevation grid.
2. **Synthetic fallback** — when no tile is configured (the common dev/offline
   case) generate a *deterministic* valley DEM so TWI still produces meaningful
   gradients. The output contract is identical, so callers cannot tell.

Flow accumulation is the D8 router in services/physics/twi.py (shared with the
Kotlin/TS mirrors for on-device TWI).
"""
from __future__ import annotations

import math
import os
from dataclasses import dataclass

import numpy as np

try:  # rasterio is optional at import time.
    import rasterio
    from rasterio.mask import mask as rio_mask

    _RASTERIO_AVAILABLE = True
except Exception:  # pragma: no cover
    rasterio = None  # type: ignore
    _RASTERIO_AVAILABLE = False


@dataclass
class DemGrid:
    elevation: list[list[float]]
    source: str          # "srtm" | "synthetic"
    cell_size_m: float
    rows: int
    cols: int


def rasterio_available() -> bool:
    return _RASTERIO_AVAILABLE


def _synthetic_dem(rows: int, cols: int, base: float = 850.0,
                   amplitude: float = 40.0) -> DemGrid:
    """Deterministic valley DEM (concave centre -> high TWI downstream)."""
    grid: list[list[float]] = []
    for r in range(rows):
        row = []
        for c in range(cols):
            x = (c / max(cols - 1, 1)) * 2 - 1
            valley = (1 - abs(x)) ** 2 * (amplitude * 0.6)
            slope = (1 - r / max(rows - 1, 1)) * amplitude
            wave = 3.0 * math.sin(r * 0.7) * math.cos(c * 0.5)
            row.append(base + slope + valley + wave)
        grid.append(row)
    return DemGrid(elevation=grid, source="synthetic", cell_size_m=30.0,
                   rows=rows, cols=cols)


def _crop_real_dem(dem_path: str, min_lon: float, min_lat: float,
                   max_lon: float, max_lat: float, out_size: int) -> DemGrid | None:
    """Crop a GeoTIFF to the bbox; return None when impossible."""
    if not _RASTERIO_AVAILABLE or not dem_path or not os.path.exists(dem_path):
        return None
    try:
        with rasterio.open(dem_path) as src:
            # GeoJSON-style bbox polygon (no shapely dependency needed).
            geom = [{
                "type": "Polygon",
                "coordinates": [[
                    [min_lon, min_lat], [max_lon, min_lat],
                    [max_lon, max_lat], [min_lon, max_lat],
                    [min_lon, min_lat],
                ]],
            }]
            out_image, _ = rio_mask(src, geom, crop=True, filled=True)
            band = out_image[0].astype(float)
            # Downsample to out_size x out_size for a bounded payload.
            if band.shape[0] > out_size or band.shape[1] > out_size:
                idx_r = np.linspace(0, band.shape[0] - 1, out_size).astype(int)
                idx_c = np.linspace(0, band.shape[1] - 1, out_size).astype(int)
                band = band[np.ix_(idx_r, idx_c)]
            # Replace nodata with the local median.
            if src.nodata is not None:
                band = np.where(band == src.nodata, np.nanmedian(band), band)
            # Cell size in metres from the transform.
            cell_m = abs(src.transform.a) * 111_320.0
            return DemGrid(elevation=band.tolist(), source="srtm",
                           cell_size_m=float(cell_m or 30.0),
                           rows=band.shape[0], cols=band.shape[1])
    except Exception:
        return None


def extract_dem(min_lon: float, min_lat: float, max_lon: float, max_lat: float,
                out_size: int = 24) -> DemGrid:
    """Return a DEM grid for the bbox: real SRTM crop if configured, else synthetic.

    `DAPE_DEM_PATH` may point at an SRTM GeoTIFF (a shared tile mounted into the
    container). Region-specific tiling can be added by extending this function.
    """
    dem_path = os.environ.get("DAPE_DEM_PATH", "")
    real = _crop_real_dem(dem_path, min_lon, min_lat, max_lon, max_lat, out_size)
    if real is not None:
        return real
    return _synthetic_dem(out_size, out_size)
