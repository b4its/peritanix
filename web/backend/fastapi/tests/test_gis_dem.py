"""Strict tests: DEM extraction (real rasterio path + synthetic fallback)."""
from __future__ import annotations

import pytest

from app.services import gis
from app.services.physics.twi import flow_accumulation


class TestSyntheticFallback:
    def test_returns_synthetic_without_tile(self, monkeypatch):
        monkeypatch.delenv("DAPE_DEM_PATH", raising=False)
        grid = gis.extract_dem(107.60, -6.92, 107.61, -6.91, out_size=12)
        assert grid.source == "synthetic"
        assert grid.rows == 12 and grid.cols == 12
        assert len(grid.elevation) == 12
        assert all(len(row) == 12 for row in grid.elevation)

    def test_missing_tile_falls_back(self, monkeypatch):
        monkeypatch.setenv("DAPE_DEM_PATH", "/nonexistent/tile.tif")
        grid = gis.extract_dem(107.60, -6.92, 107.61, -6.91, out_size=8)
        assert grid.source == "synthetic"

    def test_synthetic_is_deterministic(self, monkeypatch):
        monkeypatch.delenv("DAPE_DEM_PATH", raising=False)
        a = gis.extract_dem(107.60, -6.92, 107.61, -6.91, out_size=10)
        b = gis.extract_dem(107.60, -6.92, 107.61, -6.91, out_size=10)
        assert a.elevation == b.elevation

    def test_synthetic_produces_flow(self, monkeypatch):
        monkeypatch.delenv("DAPE_DEM_PATH", raising=False)
        grid = gis.extract_dem(107.60, -6.92, 107.61, -6.91, out_size=16)
        acc = flow_accumulation(grid.elevation)
        flat = [v for row in acc for v in row]
        # A valley DEM must concentrate flow somewhere (some cell > 1).
        assert max(flat) > 1.0


class TestRealDemPath:
    def test_crops_geotiff_when_available(self, tmp_path, monkeypatch):
        rasterio = pytest.importorskip("rasterio")
        import numpy as np
        from rasterio.transform import from_origin

        # Build a tiny 40x40 GeoTIFF covering a small area.
        path = tmp_path / "tile.tif"
        data = np.tile(np.linspace(800, 900, 40, dtype="float32"), (40, 1))
        transform = from_origin(107.60, -6.90, 0.001, 0.001)  # ~111 m cells
        with rasterio.open(path, "w", driver="GTiff", height=40, width=40,
                           count=1, dtype="float32", crs="EPSG:4326",
                           transform=transform) as dst:
            dst.write(data, 1)

        monkeypatch.setenv("DAPE_DEM_PATH", str(path))
        # Tile spans lon 107.60->107.64, lat -6.90->-6.94; crop well inside it.
        grid = gis.extract_dem(107.61, -6.93, 107.63, -6.91, out_size=12)
        assert grid.source == "srtm"
        assert grid.rows == 12 and grid.cols == 12
        # Elevations must lie within the tile's overall range (not the synthetic
        # base of ~850+slope which would exceed 900 only via the valley).
        flat = [v for row in grid.elevation for v in row]
        assert 795 <= min(flat) <= 905
        assert 795 <= max(flat) <= 905
        assert max(flat) > min(flat)  # real gradient present
        # Cell size derived from the transform (~111 m).
        assert 100 < grid.cell_size_m < 130

    def test_rasterio_available_flag(self):
        assert isinstance(gis.rasterio_available(), bool)
