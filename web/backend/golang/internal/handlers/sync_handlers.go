package handlers

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/domain"
)

// --- Offline delta sync -----------------------------------------------------

// syncDeltaReq is a batch of offline mutations from a mobile device.
type syncDeltaReq struct {
	DeviceID   string   `json:"device_id"`
	Operations []syncOp `json:"operations"`
}

type syncOp struct {
	// IdempotencyKey MUST be stable per logical mutation (device+uuid).
	IdempotencyKey string         `json:"idempotency_key"`
	Entity         string         `json:"entity"` // soil_assessment | disease_log | harvest_batch
	Op             string         `json:"op"`     // upsert
	UpdatedTS      int64          `json:"updated_ts"`
	Rev            int64          `json:"rev"`
	Data           map[string]any `json:"data"`
}

type opResult struct {
	Key    string `json:"idempotency_key"`
	Status string `json:"status"` // applied | duplicate | rejected
	Reason string `json:"reason,omitempty"`
}

// syncDelta applies offline mutations with idempotency + Last-Write-Wins.
func (d *Deps) syncDelta(c *fiber.Ctx) error {
	var req syncDeltaReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}

	results := make([]opResult, 0, len(req.Operations))
	for _, op := range req.Operations {
		if op.IdempotencyKey == "" {
			results = append(results, opResult{Status: "rejected", Reason: "missing idempotency_key"})
			continue
		}
		// Idempotency: skip if we've already processed this key within 24h.
		dup, err := d.Cache.SeenOrSet(c.Context(), op.IdempotencyKey, 24*time.Hour)
		if err != nil {
			results = append(results, opResult{Key: op.IdempotencyKey,
				Status: "rejected", Reason: "idempotency store error"})
			continue
		}
		if dup {
			results = append(results, opResult{Key: op.IdempotencyKey, Status: "duplicate"})
			continue
		}
		if err := d.applyOp(c.Context(), op); err != nil {
			results = append(results, opResult{Key: op.IdempotencyKey,
				Status: "rejected", Reason: err.Error()})
			continue
		}
		results = append(results, opResult{Key: op.IdempotencyKey, Status: "applied"})
	}

	return c.JSON(fiber.Map{
		"device_id": req.DeviceID,
		"results":   results,
		"server_ts": time.Now().Unix(),
	})
}

// ApplySyncOp applies a raw sync payload by entity (used by the retry worker).
// `data` is the JSON-encoded field map for the entity.
func (d *Deps) ApplySyncOp(ctx context.Context, entity string, data []byte) error {
	var m map[string]any
	if err := json.Unmarshal(data, &m); err != nil {
		return err
	}
	return d.applyOp(ctx, syncOp{Entity: entity, Data: m})
}

// applyOp routes a sync op to the correct repository using Last-Write-Wins.
func (d *Deps) applyOp(ctx context.Context, op syncOp) error {
	switch op.Entity {
	case "soil_assessment":
		return d.Soil.Create(ctx, &domain.SoilAssessment{
			LandID:              getStr(op.Data, "land_id"),
			SandPct:             getNum(op.Data, "sand_pct"),
			SiltPct:             getNum(op.Data, "silt_pct"),
			ClayPct:             getNum(op.Data, "clay_pct"),
			SoilClass:           getStr(op.Data, "soil_class"),
			CECMeq:              getNum(op.Data, "cec_meq"),
			PHRDeterministic:    getNum(op.Data, "ph_deterministic"),
			NitratePPM:          getNum(op.Data, "nitrate_ppm"),
			DarkChamberLuxCheck: getBool(op.Data, "dark_chamber_lux_check"),
			UpdatedTS:           op.UpdatedTS,
		})
	case "disease_log":
		return d.Disease.Create(ctx, &domain.DiseaseLog{
			LandID:           getStr(op.Data, "land_id"),
			Lon:              getNum(op.Data, "lon"),
			Lat:              getNum(op.Data, "lat"),
			LocalTWI:         getNum(op.Data, "local_twi"),
			ConfirmedDisease: getStr(op.Data, "confirmed_disease"),
			SeverityPct:      getNum(op.Data, "severity_pct"),
			ConfidenceLevel:  getStr(op.Data, "confidence_level"),
			ProtocolCode:     getStr(op.Data, "protocol_code"),
			UpdatedTS:        op.UpdatedTS,
		})
	case "harvest_batch":
		return d.Harvest.Create(ctx, &domain.HarvestBatch{
			LandID:              getStr(op.Data, "land_id"),
			BatchCode:           getStr(op.Data, "batch_code"),
			BoilingPointCalc:    getNum(op.Data, "boiling_point_calc"),
			HotWaterRatio:       getStr(op.Data, "hot_water_ratio"),
			ArchimedesSinkRatio: getNum(op.Data, "archimedes_sink_ratio"),
			GradeResult:         getStr(op.Data, "grade_result"),
			PayloadHash:         getStr(op.Data, "payload_hash"),
			CryptoSignature:     getStr(op.Data, "crypto_signature"),
			SignerPublicKey:     getStr(op.Data, "signer_public_key"),
			UpdatedTS:           op.UpdatedTS,
		})
	default:
		return fmt.Errorf("unknown entity %q", op.Entity)
	}
}

func getStr(m map[string]any, k string) string {
	if v, ok := m[k].(string); ok {
		return v
	}
	return ""
}

func getNum(m map[string]any, k string) float64 {
	if v, ok := m[k].(float64); ok {
		return v
	}
	return 0
}

func getBool(m map[string]any, k string) bool {
	if v, ok := m[k].(bool); ok {
		return v
	}
	return false
}
