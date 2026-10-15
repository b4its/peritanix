package handlers

import (
	"strconv"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/domain"
)

func nowTS() int64 { return time.Now().Unix() }

// --- Soil -------------------------------------------------------------------

type soilSyncReq struct {
	LandID              string           `json:"land_id"`
	SandThickness       float64          `json:"sand_thickness"`
	SiltThickness       float64          `json:"silt_thickness"`
	ClayThickness       float64          `json:"clay_thickness"`
	MeasuredRGB         []float64        `json:"measured_rgb"`
	WhiteRefRGB         []float64        `json:"white_ref_rgb"`
	DarkChamberLuxCheck bool             `json:"dark_chamber_lux_check"`
	PHCurve             []map[string]any `json:"ph_curve"`
}

// soilSync runs Stokes + photometry in the ML engine and persists the result.
func (d *Deps) soilSync(c *fiber.Ctx) error {
	var req soilSyncReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.LandID == "" {
		return fiber.NewError(fiber.StatusBadRequest, "land_id required")
	}

	// 1) Stokes texture -> USDA class + CEC (ML engine).
	texture, err := d.ML.SoilCalibrate(c.Context(), map[string]any{
		"land_id": req.LandID,
		"layers": map[string]any{
			"sand_thickness": req.SandThickness,
			"silt_thickness": req.SiltThickness,
			"clay_thickness": req.ClayThickness,
		},
		"dark_chamber_lux_check": req.DarkChamberLuxCheck,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "soil engine: "+err.Error())
	}

	// 2) Dark-chamber photometry -> deterministic pH (ML engine), optional.
	var ph, nitrate float64
	if len(req.MeasuredRGB) == 3 {
		white := req.WhiteRefRGB
		if len(white) != 3 {
			white = []float64{255, 255, 255}
		}
		curve := req.PHCurve
		if len(curve) == 0 {
			curve = defaultPHCurve()
		}
		photo, perr := d.ML.Photometry(c.Context(), map[string]any{
			"measured_rgb":  req.MeasuredRGB,
			"white_ref_rgb": white,
			"curve":         curve,
		})
		if perr == nil {
			if v, ok := photo["matched_value"].(float64); ok {
				ph = v
			}
		}
	}

	// 3) Prescriptive fertilizer plan (uses texture + pH + HST).
	plan, _ := d.ML.FertilizerPlan(c.Context(), map[string]any{
		"ph": ph, "cec_meq": num(texture["cec_meq"]), "hst": 20,
		"target_yield_t_ha": 20.0, "soil_class": str(texture["soil_class"]),
	})

	assessment := &domain.SoilAssessment{
		LandID:              req.LandID,
		SandPct:             num(texture["sand_pct"]),
		SiltPct:             num(texture["silt_pct"]),
		ClayPct:             num(texture["clay_pct"]),
		SoilClass:           str(texture["soil_class"]),
		CECMeq:              num(texture["cec_meq"]),
		PHRDeterministic:    ph,
		NitratePPM:          nitrate,
		DarkChamberLuxCheck: req.DarkChamberLuxCheck,
		UpdatedTS:           nowTS(),
	}
	if err := d.Soil.Create(c.Context(), assessment); err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	return c.Status(fiber.StatusCreated).JSON(fiber.Map{
		"assessment": assessment,
		"fertilizer": plan,
	})
}

func (d *Deps) soilList(c *fiber.Ctx) error {
	list, err := d.Soil.ListByLand(c.Context(), c.Params("id"))
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	return c.JSON(fiber.Map{"assessments": list})
}

// soilFertilityGeoJSON serves the dashboard fertility map layer.
func (d *Deps) soilFertilityGeoJSON(c *fiber.Ctx) error {
	js, err := d.Soil.FertilityGeoJSON(c.Context())
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	c.Set("Content-Type", "application/geo+json")
	return c.Send(js)
}

// gddHistory serves the GDD accumulation time series for the chart.
func (d *Deps) gddHistory(c *fiber.Ctx) error {
	days := 30
	if v := c.Query("days"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 && n <= 365 {
			days = n
		}
	}
	pts, err := d.GDD.History(c.Context(), c.Params("id"), days)
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	return c.JSON(fiber.Map{"land_id": c.Params("id"), "series": pts})
}

func num(v any) float64 {
	if f, ok := v.(float64); ok {
		return f
	}
	return 0
}

func str(v any) string {
	if s, ok := v.(string); ok {
		return s
	}
	return ""
}

// defaultPHCurve is a fallback reagent calibration (RGB -> pH nodes).
func defaultPHCurve() []map[string]any {
	return []map[string]any{
		{"value": 4.0, "lab": []float64{62.0, 45.0, 38.0}},   // acid -> yellow/red
		{"value": 6.0, "lab": []float64{78.0, -20.0, 52.0}},  // ~neutral green
		{"value": 8.0, "lab": []float64{70.0, -30.0, -18.0}}, // alkaline blue
	}
}
