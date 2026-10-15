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

-- Distance (m) from a point to the nearest edge of the land boundary.
-- Low distances correlate with wind/vector-borne entry (EDGE_WIND_GRADIENT).
CREATE OR REPLACE FUNCTION distance_to_land_edge_m(
    p_land_id UUID,
    p_point   GEOMETRY
) RETURNS FLOAT AS $$
    SELECT COALESCE(ST_Distance(
        p_point::geography,
        ST_Boundary(l.boundary)::geography
    ), -1.0)
    FROM lands l
    WHERE l.id = p_land_id;
$$ LANGUAGE sql STABLE;

-- Bearing (deg, 0=N, clockwise) of a point relative to the land centroid.
-- Used to align symptom positions against the prevailing wind direction.
-- NOTE: MOD() is integer-only in PostgreSQL; use the double-precision identity
--   x - floor(x/360)*360   to normalise into [0, 360).
CREATE OR REPLACE FUNCTION bearing_from_centroid_deg(
    p_land_id UUID,
    p_point   GEOMETRY
) RETURNS FLOAT AS $$
    SELECT COALESCE(
        (
            (90 - DEGREES(ST_Azimuth(ST_Centroid(l.boundary), p_point)) + 360)
            - floor((90 - DEGREES(ST_Azimuth(ST_Centroid(l.boundary), p_point)) + 360) / 360.0) * 360
        ),
        -1.0
    )
    FROM lands l
    WHERE l.id = p_land_id;
$$ LANGUAGE sql STABLE;

-- One-shot spatial context used by the Bayesian orchestration: nearby count,
-- distance to edge and bearing in a single round-trip.
CREATE OR REPLACE FUNCTION spatial_diagnosis_context(
    p_land_id   UUID,
    p_point     GEOMETRY,
    p_radius_m  FLOAT DEFAULT 50.0
) RETURNS TABLE(
    nearby_infected  INTEGER,
    dist_edge_m      FLOAT,
    bearing_deg      FLOAT
) AS $$
    SELECT
        nearby_infected_count(p_land_id, p_point, p_radius_m),
        distance_to_land_edge_m(p_land_id, p_point),
        bearing_from_centroid_deg(p_land_id, p_point);
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
