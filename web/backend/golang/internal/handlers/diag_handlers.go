package handlers

import (
	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/domain"
	"github.com/peritanix/dape-backend/internal/epidemiology"
)

// --- Diagnostics ------------------------------------------------------------

type diagReq struct {
	LandID         string             `json:"land_id"`
	Lon            float64            `json:"lon"`
	Lat            float64            `json:"lat"`
	LocalTWI       *float64           `json:"local_twi"`
	Visual         map[string]float64 `json:"visual"`
	Spatial        map[string]float64 `json:"spatial"`
	OozeFlow       map[string]any     `json:"ooze_optical_flow"`
	TransAtten     *float64           `json:"trans_illumination_attenuation_pct"`
	SeverityPct    *float64           `json:"severity_pct"`
	PrevailingWind *float64           `json:"prevailing_wind_deg"`
}

// diagEvaluate orchestrates spatial context -> Bayesian fusion -> persist.
func (d *Deps) diagEvaluate(c *fiber.Ctx) error {
	var req diagReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.LandID == "" {
		return fiber.NewError(fiber.StatusBadRequest, "land_id required")
	}

	const radiusM = 50.0
	// 1) PostGIS spatial context: nearby count, edge distance, bearing.
	sc, _ := d.Disease.SpatialContext(c.Context(), req.LandID, req.Lon, req.Lat, radiusM)

	// 2) Spatial pattern classification (Moran's I + edge/trough bias) from the
	//    nearby infection points — mirrors the Python classifier.
	pattern := "CLUSTERED_UNSPECIFIED"
	if pts, err := d.Disease.NearbyPoints(c.Context(), req.LandID, req.Lon, req.Lat, radiusM); err == nil && len(pts) >= 2 {
		ep := make([]epidemiology.Point, len(pts))
		edgeDist := make([]float64, len(pts))
		twiVals := make([]float64, len(pts))
		for i, p := range pts {
			ep[i] = epidemiology.Point{Lon: p.Lon, Lat: p.Lat, Severity: p.Severity}
			edgeDist[i] = p.DistEdgeM
			twiVals[i] = p.LocalTWI
		}
		pattern, _ = epidemiology.PatternFromPoints(ep, edgeDist, twiVals,
			radiusM, 5.0, 8.0)
	}

	// 3) Build the ML payload. Non-nil maps so Pydantic accepts {} not null.
	spatialContext := map[string]any{
		"nearby_infected":    sc.NearbyInfected,
		"distance_to_edge_m": sc.DistEdgeM,
		"bearing_deg":        sc.BearingDeg,
		"pattern":            pattern,
	}
	if req.PrevailingWind != nil {
		spatialContext["prevailing_wind_deg"] = *req.PrevailingWind
	}

	in := map[string]any{
		"land_id":         req.LandID,
		"point":           []float64{req.Lon, req.Lat},
		"visual":          nonNilMap(req.Visual),
		"spatial":         nonNilMap(req.Spatial),
		"prior":           map[string]float64{},
		"spatial_context": spatialContext,
	}
	if req.LocalTWI != nil {
		in["local_twi"] = *req.LocalTWI
	}
	if req.OozeFlow != nil {
		in["ooze_optical_flow"] = req.OozeFlow
	}
	if req.TransAtten != nil {
		in["trans_illumination_attenuation_pct"] = *req.TransAtten
	}
	if req.SeverityPct != nil {
		in["severity_pct"] = *req.SeverityPct
	}

	res, err := d.ML.BayesianDiagnosis(c.Context(), in)
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "bayesian engine: "+err.Error())
	}

	// Persist the confirmed diagnosis.
	log := &domain.DiseaseLog{
		LandID:                 req.LandID,
		Lon:                    req.Lon,
		Lat:                    req.Lat,
		LocalTWI:               deref(req.LocalTWI),
		PosteriorProbabilities: mapFloat(res["posterior_probabilities"]),
		ConfirmedDisease:       str(res["final_verdict"]),
		SeverityPct:            deref(req.SeverityPct),
		ConfidenceLevel:        str(res["confidence_level"]),
		ProtocolCode:           str(res["recommended_protocol_code"]),
		UpdatedTS:              nowTS(),
	}
	if err := d.Disease.Create(c.Context(), log); err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}

	return c.JSON(fiber.Map{
		"diagnosis":       log,
		"bayesian":        res,
		"spatial_context": spatialContext,
	})
}

func (d *Deps) diseaseList(c *fiber.Ctx) error {
	list, err := d.Disease.ListByLand(c.Context(), c.Params("id"))
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	return c.JSON(fiber.Map{"diseases": list})
}

func (d *Deps) diseaseGeoJSON(c *fiber.Ctx) error {
	js, err := d.Disease.GeoJSON(c.Context(), c.Params("id"))
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	c.Set("Content-Type", "application/geo+json")
	return c.Send(js)
}

func deref(f *float64) float64 {
	if f == nil {
		return 0
	}
	return *f
}

func mapFloat(v any) map[string]float64 {
	out := map[string]float64{}
	m, ok := v.(map[string]any)
	if !ok {
		return out
	}
	for k, val := range m {
		if f, ok := val.(float64); ok {
			out[k] = f
		}
	}
	return out
}

// nonNilMap guarantees a non-nil map so JSON encodes {} instead of null.
func nonNilMap(m map[string]float64) map[string]float64 {
	if m == nil {
		return map[string]float64{}
	}
	return m
}
