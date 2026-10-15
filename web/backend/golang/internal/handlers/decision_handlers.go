package handlers

import (
	"github.com/gofiber/fiber/v2"
)

// --- Yield projection (Opsi 3) ----------------------------------------------

type yieldReq struct {
	LandID       string  `json:"land_id"`
	GreenDark    int     `json:"green_dark"`
	RedBlush     int     `json:"red_blush"`
	RedFull      int     `json:"red_full"`
	TreesPerBed  int     `json:"trees_per_bed"`
	SampledTrees int     `json:"sampled_trees"`
	HorizonDays  int     `json:"horizon_days"`
	GDDPerDay    float64 `json:"gdd_per_day"`
}

// yieldProjection estimates harvestable tonnage over a 14-day horizon.
func (d *Deps) yieldProjection(c *fiber.Ctx) error {
	var req yieldReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	in := map[string]any{
		"land_id": req.LandID, "green_dark": req.GreenDark,
		"red_blush": req.RedBlush, "red_full": req.RedFull,
		"trees_per_bed": req.TreesPerBed, "sampled_trees": req.SampledTrees,
	}
	if req.HorizonDays > 0 {
		in["horizon_days"] = req.HorizonDays
	}
	if req.GDDPerDay > 0 {
		in["gdd_per_day"] = req.GDDPerDay
	}
	res, err := d.ML.YieldProjection(c.Context(), in)
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "yield engine: "+err.Error())
	}
	return c.JSON(res)
}

// --- Prescriptive protocols (Opsi 1D) ---------------------------------------

type protocolReq struct {
	VerdictKey  string  `json:"verdict_key"`
	SeverityPct float64 `json:"severity_pct"`
	Confidence  string  `json:"confidence"`
}

func (d *Deps) protocolPrescribe(c *fiber.Ctx) error {
	var req protocolReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.VerdictKey == "" {
		return fiber.NewError(fiber.StatusBadRequest, "verdict_key required")
	}
	if req.Confidence == "" {
		req.Confidence = "MEDIUM"
	}
	res, err := d.ML.ProtocolPrescribe(c.Context(), map[string]any{
		"verdict_key": req.VerdictKey, "severity_pct": req.SeverityPct,
		"confidence": req.Confidence,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "protocol engine: "+err.Error())
	}
	return c.JSON(res)
}

func (d *Deps) protocolCatalog(c *fiber.Ctx) error {
	res, err := d.ML.ProtocolCatalog(c.Context())
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "protocol engine: "+err.Error())
	}
	return c.JSON(res)
}

// --- Physical-evidence trace validation -------------------------------------

type traceReq struct {
	LandID  string           `json:"land_id"`
	Samples []map[string]any `json:"samples"`
	TargetS float64          `json:"target_s"`
}

func (d *Deps) validateTrace(c *fiber.Ctx) error {
	var req traceReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	in := map[string]any{"samples": req.Samples}
	if req.TargetS > 0 {
		in["target_s"] = req.TargetS
	}
	res, err := d.ML.ValidateTrace(c.Context(), in)
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "trace engine: "+err.Error())
	}
	return c.JSON(res)
}
