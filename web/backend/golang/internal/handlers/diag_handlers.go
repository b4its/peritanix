package handlers

import (
	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/domain"
)

// --- Diagnostics ------------------------------------------------------------

type diagReq struct {
	LandID        string             `json:"land_id"`
	Lon           float64            `json:"lon"`
	Lat           float64            `json:"lat"`
	LocalTWI      *float64           `json:"local_twi"`
	Visual        map[string]float64 `json:"visual"`
	Spatial       map[string]float64 `json:"spatial"`
	OozeFlow      map[string]any     `json:"ooze_optical_flow"`
	TransAtten    *float64           `json:"trans_illumination_attenuation_pct"`
	SeverityPct   *float64           `json:"severity_pct"`
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

	// Spatial context from PostGIS: nearby infected count (radius 50 m).
	nearby, _ := d.Disease.NearbyCount(c.Context(), req.LandID, req.Lon, req.Lat, 50.0)

	// Build the payload for the ML Bayesian engine.
	in := map[string]any{
		"land_id": req.LandID,
		"point":   []float64{req.Lon, req.Lat},
		"visual":  req.Visual,
		"spatial": req.Spatial,
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
		"diagnosis":      log,
		"bayesian":       res,
		"nearby_infected": nearby,
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
