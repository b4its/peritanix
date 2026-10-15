package handlers

import (
	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/domain"
)

// --- Post-harvest -----------------------------------------------------------

type calorimetryReq struct {
	ElevationM float64 `json:"elevation_m"`
	WellTempC  float64 `json:"well_temp_c"`
	TargetC    float64 `json:"target_c"`
	Scale      float64 `json:"scale"`
}

func (d *Deps) calorimetry(c *fiber.Ctx) error {
	var req calorimetryReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.WellTempC == 0 {
		req.WellTempC = 27.0
	}
	if req.TargetC == 0 {
		req.TargetC = 52.0
	}
	if req.Scale == 0 {
		req.Scale = 9.0
	}
	res, err := d.ML.Calorimetry(c.Context(), map[string]any{
		"elevation_m": req.ElevationM, "well_temp_c": req.WellTempC,
		"target_c": req.TargetC, "scale": req.Scale,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "calorimetry engine: "+err.Error())
	}
	return c.JSON(res)
}

type archimedesReq struct {
	SinkCount    int     `json:"sink_count"`
	NeutralCount int     `json:"neutral_count"`
	FloatCount   int     `json:"float_count"`
	SaltG        float64 `json:"salt_g"`
	WaterML      float64 `json:"water_ml"`
}

func (d *Deps) archimedes(c *fiber.Ctx) error {
	var req archimedesReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	res, err := d.ML.Archimedes(c.Context(), map[string]any{
		"sink_count": req.SinkCount, "neutral_count": req.NeutralCount,
		"float_count": req.FloatCount, "salt_g": req.SaltG, "water_ml": req.WaterML,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "archimedes engine: "+err.Error())
	}
	return c.JSON(res)
}

type harvestCreateReq struct {
	LandID              string  `json:"land_id"`
	BatchCode           string  `json:"batch_code"`
	BoilingPointCalc    float64 `json:"boiling_point_calc"`
	HotWaterRatio       string  `json:"hot_water_ratio"`
	ArchimedesSinkRatio float64 `json:"archimedes_sink_ratio"`
	GradeResult         string  `json:"grade_result"`
	PayloadHash         string  `json:"payload_hash"`
	CryptoSignature     string  `json:"crypto_signature"`
	SignerPublicKey     string  `json:"signer_public_key"`
}

// harvestCreate persists a signed harvest batch (from mobile or web).
func (d *Deps) harvestCreate(c *fiber.Ctx) error {
	var req harvestCreateReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.LandID == "" || req.BatchCode == "" {
		return fiber.NewError(fiber.StatusBadRequest, "land_id and batch_code required")
	}
	h := &domain.HarvestBatch{
		LandID: req.LandID, BatchCode: req.BatchCode,
		BoilingPointCalc: req.BoilingPointCalc, HotWaterRatio: req.HotWaterRatio,
		ArchimedesSinkRatio: req.ArchimedesSinkRatio, GradeResult: req.GradeResult,
		PayloadHash: req.PayloadHash, CryptoSignature: req.CryptoSignature,
		SignerPublicKey: req.SignerPublicKey, UpdatedTS: nowTS(),
	}
	if err := d.Harvest.Create(c.Context(), h); err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	return c.Status(fiber.StatusCreated).JSON(h)
}

func (d *Deps) harvestGet(c *fiber.Ctx) error {
	h, err := d.Harvest.GetByBatchCode(c.Context(), c.Params("code"))
	if err != nil {
		return fiber.NewError(fiber.StatusNotFound, "batch not found")
	}
	return c.JSON(h)
}
