-- =============================================================================
-- DAPE / Peritanix — Spatial helpers (PostGIS)
-- =============================================================================

-- Local ups lope area is delivered as a raster package; these helpers serve the
-- nearby-infection queries used by the Bayesian spatiotemporal pipeline.

CREATE OR REPLACE FUNCTION nearby_infected_count(
    p_land_id   UUID,
    p_point     GEOMETRY,
    p_radius_m  FLOAT DEFAULT 50.0
) RETURNS INTEGER AS $$
    SELECT COUNT(*)::INTEGER
    FROM disease_logs d
    WHERE d.land_id = p_land_id
      AND d.point_location IS NOT NULL
      AND ST_DWithin(d.point_location::geography, p_point::geography, p_radius_m);
$$ LANGUAGE sql STABLE;

-- Dominant infection distribution: classify as edge-gradient / drainage-trough /
-- uniform by comparing Moran's I computed app-side; this helper returns the raw
-- point cloud as GeoJSON for the dashboard heatmap.
CREATE OR REPLACE FUNCTION disease_geojson(p_land_id UUID)
RETURNS JSON AS $$
    SELECT COALESCE(json_agg(feature), '[]'::json) FROM (
        SELECT json_build_object(
            'type', 'Feature',
            'geometry', ST_AsGeoJSON(d.point_location)::json,
            'properties', json_build_object(
                'id', d.id,
                'disease', d.confirmed_disease,
                'severity_pct', d.severity_pct,
                'local_twi', d.local_twi,
                'confidence', d.confidence_level,
                'created_at', d.created_at
            )
        ) AS feature
        FROM disease_logs d
        WHERE d.land_id = p_land_id AND d.point_location IS NOT NULL
    ) sub;
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION land_geojson()
RETURNS JSON AS $$
    SELECT COALESCE(json_agg(feature), '[]'::json) FROM (
        SELECT json_build_object(
            'type', 'Feature',
            'geometry', ST_AsGeoJSON(l.boundary)::json,
            'properties', json_build_object(
                'id', l.id,
                'name', l.name,
                'area_ha', l.area_ha,
                'phase', l.current_phase,
                'gdd', l.accumulated_gdd,
                'elevation_m', l.elevation_m
            )
        ) AS feature
        FROM lands l
    ) sub;
$$ LANGUAGE sql STABLE;
