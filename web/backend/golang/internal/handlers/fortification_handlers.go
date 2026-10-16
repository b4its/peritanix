package handlers

import (
	"strconv"
	"strings"

	"github.com/gofiber/fiber/v2"
)

// --- Fortification Lock protocol guard (TAHAP 4.2) --------------------------
//
// During peak anthesis (GDD 350–699) the pericarp cell wall must be fortified
// with Ca+B+Si; high-nitrogen inputs (urea / N-dominant) soften the cell wall
// and increase susceptibility to pathogens. The spec requires the app to BLOCK
// such input rather than merely warn. This endpoint is the single source of
// truth the mobile and web clients call before persisting a fertilization log.

// fertilizerInput describes a fertilization log attempt.
type fertilizerInput struct {
	LandID      string  `json:"land_id"`
	Product     string  `json:"product"`      // e.g. "Urea", "NPK 16-16-16", "KNO3"
	NPKRatio    string  `json:"npk_ratio"`    // e.g. "46-0-0"
	NitrogenPct float64 `json:"nitrogen_pct"` // e.g. 46 for urea
	AppliedG    float64 `json:"applied_g"`
}

// highNitrogenNames are products that are N-dominant.
var highNitrogenNames = []string{"urea", "za", "ammonium", "amoniak", "ammonia", "n-tinggi"}

// nDominant reports whether this input is high-nitrogen (should be blocked
// during fortification).
func nDominant(in fertilizerInput) bool {
	name := strings.ToLower(strings.TrimSpace(in.Product))
	for _, h := range highNitrogenNames {
		if strings.Contains(name, h) {
			return true
		}
	}
	// NPK ratio "46-0-0" style: N fraction >= 30 % is N-dominant (K/Ca focus
	// during fortification).
	if in.NitrogenPct >= 30.0 {
		return true
	}
	ratio := strings.TrimSpace(in.NPKRatio)
	if ratio != "" {
		parts := strings.Split(ratio, "-")
		if len(parts) == 3 {
			if n, err := strconv.ParseFloat(parts[0], 64); err == nil && n >= 30 {
				return true
			}
		}
	}
	return false
}

// fortificationGuard evaluates whether a fertilization input is allowed for a
// land, based on its current phase.
func (d *Deps) fortificationGuard(c *fiber.Ctx) error {
	var req fertilizerInput
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.LandID == "" {
		return fiber.NewError(fiber.StatusBadRequest, "land_id required")
	}
	land, err := d.Lands.GetByID(c.Context(), req.LandID)
	if err != nil {
		return fiber.NewError(fiber.StatusNotFound, "land not found")
	}

	locked := land.CurrentPhase == "FORTIFICATION_LOCKED"
	blocked := locked && nDominant(req)

	resp := fiber.Map{
		"land_id":       land.ID,
		"phase":         land.CurrentPhase,
		"plant_gdd":     land.AccumulatedGDD,
		"fortification": locked,
		"blocked":       blocked,
		"allowed":       !blocked,
	}
	if blocked {
		resp["reason"] = "Fase Antesis (pembungaan puncak): input Nitrogen tinggi DIBLOKIR " +
			"karena memperlemah dinding sel (pericarp) dan memicu kerontokan bunga / " +
			"kerentanan patogen."
		resp["required_protocol"] = "CA_B_SI_FORTIFICATION"
		resp["guidance"] = "Gunakan Kalsium Nitrat + Boron + Asam Silikat. Tunda Urea/N " +
			"sampai fase pembuahan (GDD ≥ 700)."
	} else if locked {
		resp["guidance"] = "Fase Antesis: prioritaskan Ca+B+Si untuk penguatan dinding sel."
	}
	return c.JSON(resp)
}
