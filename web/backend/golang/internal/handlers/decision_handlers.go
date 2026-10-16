package handlers

import (
	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/domain"
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

// --- Fruit counting (Opsi 3: auto-fill yield counts from a canopy photo) -----

type fruitCountReq struct {
	LandID    string        `json:"land_id"`
	Image     [][][]float64 `json:"image"`
	MinAreaPx int           `json:"min_area_px"`
	MaxAreaPx int           `json:"max_area_px"`
}

// fruitCount auto-counts fruit by ripeness class from a canopy photo.
func (d *Deps) fruitCount(c *fiber.Ctx) error {
	var req fruitCountReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if len(req.Image) == 0 {
		return fiber.NewError(fiber.StatusBadRequest, "image (HxWx3) required")
	}
	in := map[string]any{"image": req.Image}
	if req.MinAreaPx > 0 {
		in["min_area_px"] = req.MinAreaPx
	}
	if req.MaxAreaPx > 0 {
		in["max_area_px"] = req.MaxAreaPx
	}
	res, err := d.ML.FruitCount(c.Context(), in)
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "fruit-count engine: "+err.Error())
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

// --- Harvest decision (post-harvest action fusion) --------------------------

type harvestDecisionReq struct {
	AccumulatedGDD      float64 `json:"accumulated_gdd"`
	CurrentPhase        string  `json:"current_phase"`
	ArchimedesSinkRatio float64 `json:"archimedes_sink_ratio"`
	CurvatureRatio      float64 `json:"curvature_ratio"`
	RedPct              float64 `json:"red_pct"`
	DefectPct           float64 `json:"defect_pct"`
	PriceTrend14d       float64 `json:"price_trend_14d"`
}

// harvestDecision fuses GDD phase + grading + archimedes + market signal into
// a single sell/hold/sort recommendation.
func (d *Deps) harvestDecision(c *fiber.Ctx) error {
	var req harvestDecisionReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.CurrentPhase == "" {
		req.CurrentPhase = "FRUITING"
	}
	if req.CurvatureRatio == 0 {
		req.CurvatureRatio = 1.0
	}
	res, err := d.ML.HarvestDecision(c.Context(), map[string]any{
		"accumulated_gdd": req.AccumulatedGDD, "current_phase": req.CurrentPhase,
		"archimedes_sink_ratio": req.ArchimedesSinkRatio,
		"curvature_ratio":       req.CurvatureRatio, "red_pct": req.RedPct,
		"defect_pct": req.DefectPct, "price_trend_14d": req.PriceTrend14d,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "harvest engine: "+err.Error())
	}
	return c.JSON(res)
}

// --- Microclimate sensor ingest (ESP32/BLE logger) --------------------------

type climateIngestReq struct {
	LandID   string                  `json:"land_id"`
	DeviceID string                  `json:"device_id"`
	Readings []domain.ClimateReading `json:"readings"`
}

// climateIngest stores a batch of sensor readings pulled from the field logger
// by the phone (BLE), then returns the current EWS assessment for that land.
func (d *Deps) climateIngest(c *fiber.Ctx) error {
	var req climateIngestReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.LandID == "" || len(req.Readings) == 0 {
		return fiber.NewError(fiber.StatusBadRequest, "land_id and readings required")
	}
	n, err := d.Climate.Ingest(c.Context(), req.LandID, req.DeviceID, req.Readings)
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	// Immediately compute the EWS from the full recent window.
	ews, eerr := d.computeEws(c, req.LandID, 72)
	resp := fiber.Map{"ingested": n, "land_id": req.LandID}
	if eerr == nil {
		resp["ews"] = ews
	}
	return c.Status(fiber.StatusCreated).JSON(resp)
}

// climateEws returns the 72h EWS from the land's stored readings.
func (d *Deps) climateEws(c *fiber.Ctx) error {
	ews, err := d.computeEws(c, c.Params("id"), 72)
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, err.Error())
	}
	return c.JSON(ews)
}

// computeEws loads readings and calls the ML EWS engine.
func (d *Deps) computeEws(c *fiber.Ctx, landID string, hours int) (map[string]any, error) {
	series, err := d.Climate.Series(c.Context(), landID, hours)
	if err != nil {
		return nil, err
	}
	if len(series) < 3 {
		return map[string]any{
			"land_id": landID, "insufficient_data": true,
			"readings": len(series),
		}, nil
	}
	rh := make([]float64, len(series))
	temps := make([]float64, len(series))
	for i, rd := range series {
		rh[i] = rd.RH
		temps[i] = rd.TempC
	}
	return d.ML.EwsMicroclimate(c.Context(), map[string]any{
		"land_id": landID, "rh_series": rh, "t_series": temps, "horizon_h": hours,
	})
}

// --- Economics (farm-level impact) ------------------------------------------

type economicsReq struct {
	AreaHa                 float64 `json:"area_ha"`
	BaselineYieldTHa       float64 `json:"baseline_yield_t_ha"`
	PricePerKg             float64 `json:"price_per_kg"`
	FertilizerCostPerHaIDR float64 `json:"fertilizer_cost_per_ha_idr"`
	FertilizerSavingPct    float64 `json:"fertilizer_saving_pct"`
	YieldUpliftPct         float64 `json:"yield_uplift_pct"`
	OutbreakProbability    float64 `json:"outbreak_probability"`
	LossIfLatePct          float64 `json:"loss_if_late_pct"`
	LossIfEarlyPct         float64 `json:"loss_if_early_pct"`
}

// economics turns DAPE recommendations into rupiah (fertilizer saving, avoided
// crop loss, yield uplift) so a farmer or cooperative can judge the ROI.
func (d *Deps) economics(c *fiber.Ctx) error {
	var req economicsReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.AreaHa <= 0 {
		return fiber.NewError(fiber.StatusBadRequest, "area_ha must be > 0")
	}
	// Apply sensible defaults for any omitted field.
	if req.BaselineYieldTHa == 0 {
		req.BaselineYieldTHa = 12
	}
	if req.PricePerKg == 0 {
		req.PricePerKg = 25000
	}
	if req.FertilizerCostPerHaIDR == 0 {
		req.FertilizerCostPerHaIDR = 8_000_000
	}
	if req.FertilizerSavingPct == 0 {
		req.FertilizerSavingPct = 0.25
	}
	if req.YieldUpliftPct == 0 {
		req.YieldUpliftPct = 0.10
	}
	if req.OutbreakProbability == 0 {
		req.OutbreakProbability = 0.35
	}
	if req.LossIfLatePct == 0 {
		req.LossIfLatePct = 0.5
	}
	if req.LossIfEarlyPct == 0 {
		req.LossIfEarlyPct = 0.12
	}
	res, err := d.ML.Economics(c.Context(), map[string]any{
		"area_ha": req.AreaHa, "baseline_yield_t_ha": req.BaselineYieldTHa,
		"price_per_kg":               req.PricePerKg,
		"fertilizer_cost_per_ha_idr": req.FertilizerCostPerHaIDR,
		"fertilizer_saving_pct":      req.FertilizerSavingPct,
		"yield_uplift_pct":           req.YieldUpliftPct,
		"outbreak_probability":       req.OutbreakProbability,
		"loss_if_late_pct":           req.LossIfLatePct,
		"loss_if_early_pct":          req.LossIfEarlyPct,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "economics engine: "+err.Error())
	}
	return c.JSON(res)
}

// --- Protocol rules (field-workflow triggers) -------------------------------

type protocolRulesReq struct {
	LandID              string   `json:"land_id"`
	Symptoms            []string `json:"symptoms"`
	LocalTWI            *float64 `json:"local_twi"`
	AlreadyRecordedOoze bool     `json:"already_recorded_ooze"`
}

// protocolRules evaluates diagnostic decision rules; notably it can MANDATE the
// destructive ooze test when a wilting plant sits in a wet (high-TWI) cell,
// where Ralstonia and Fusarium cannot be told apart from a photo.
func (d *Deps) protocolRules(c *fiber.Ctx) error {
	var req protocolRulesReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	twi := 0.0
	if req.LocalTWI != nil {
		twi = *req.LocalTWI
	}
	symptoms := req.Symptoms
	if symptoms == nil {
		symptoms = []string{}
	}
	res, err := d.ML.ProtocolRules(c.Context(), map[string]any{
		"symptoms": symptoms, "local_twi": twi,
		"already_recorded_ooze": req.AlreadyRecordedOoze,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "protocol rules: "+err.Error())
	}
	return c.JSON(res)
}
