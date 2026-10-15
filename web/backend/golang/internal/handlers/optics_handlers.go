package handlers

import (
	"github.com/gofiber/fiber/v2"
)

// --- Multi-modal optics (mobile uploads pre-processed frames) --------------

type mfsrReq struct {
	Frames               [][][]float64 `json:"frames"`
	SharpnessRejectRatio float64       `json:"sharpness_reject_ratio"`
}

func (d *Deps) mfsrStack(c *fiber.Ctx) error {
	var req mfsrReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	ratio := req.SharpnessRejectRatio
	if ratio == 0 {
		ratio = 0.5
	}
	res, err := d.ML.MfsrStack(c.Context(), map[string]any{
		"frames": req.Frames, "sharpness_reject_ratio": ratio,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "mfsr engine: "+err.Error())
	}
	return c.JSON(res)
}

type transIllumReq struct {
	LandID    string      `json:"land_id"`
	Intensity [][]float64 `json:"intensity"`
	SigmaK    float64     `json:"sigma_k"`
}

func (d *Deps) transIllumination(c *fiber.Ctx) error {
	var req transIllumReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	sigma := req.SigmaK
	if sigma == 0 {
		sigma = 2.0
	}
	res, err := d.ML.TransIllumination(c.Context(), map[string]any{
		"intensity": req.Intensity, "sigma_k": sigma,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "trans-illum engine: "+err.Error())
	}
	return c.JSON(res)
}

type oozeReq struct {
	LandID string        `json:"land_id"`
	Frames [][][]float64 `json:"frames"`
}

func (d *Deps) oozeOpticalFlow(c *fiber.Ctx) error {
	var req oozeReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	res, err := d.ML.OozeOpticalFlow(c.Context(), map[string]any{"frames": req.Frames})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "ooze engine: "+err.Error())
	}
	return c.JSON(res)
}

// --- Post-harvest grading ---------------------------------------------------

type gradeReq struct {
	LandID       string       `json:"land_id"`
	Contour      [][2]float64 `json:"contour"`
	MeanAChannel float64      `json:"mean_a_channel"`
	LesionArea   float64      `json:"lesion_area"`
	TotalArea    float64      `json:"total_area"`
}

func (d *Deps) gradeFruit(c *fiber.Ctx) error {
	var req gradeReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	contour := make([][]float64, len(req.Contour))
	for i, p := range req.Contour {
		contour[i] = []float64{p[0], p[1]}
	}
	res, err := d.ML.GradeFruit(c.Context(), map[string]any{
		"contour": contour, "mean_a_channel": req.MeanAChannel,
		"lesion_area": req.LesionArea, "total_area": req.TotalArea,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "grading engine: "+err.Error())
	}
	return c.JSON(res)
}

// --- Microclimate EWS -------------------------------------------------------

type ewsReq struct {
	LandID   string    `json:"land_id"`
	RHEries  []float64 `json:"rh_series"`
	TSeries  []float64 `json:"t_series"`
	HorizonH int       `json:"horizon_h"`
}

func (d *Deps) ewsMicroclimate(c *fiber.Ctx) error {
	var req ewsReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	horizon := req.HorizonH
	if horizon == 0 {
		horizon = 72
	}
	res, err := d.ML.EwsMicroclimate(c.Context(), map[string]any{
		"rh_series": req.RHEries, "t_series": req.TSeries, "horizon_h": horizon,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "ews engine: "+err.Error())
	}
	return c.JSON(res)
}

// --- Anti-fraud image forensics ---------------------------------------------

type forensicReq struct {
	ImageBase64      string   `json:"image_base64"`
	KnownHashes      []string `json:"known_hashes"`
	ELAThreshold     float64  `json:"ela_threshold"`
	DuplicateHamming int      `json:"duplicate_hamming_max"`
}

func (d *Deps) imageAudit(c *fiber.Ctx) error {
	var req forensicReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.ImageBase64 == "" {
		return fiber.NewError(fiber.StatusBadRequest, "image_base64 required")
	}
	ela := req.ELAThreshold
	if ela == 0 {
		ela = 120.0
	}
	ham := req.DuplicateHamming
	if ham == 0 {
		ham = 4
	}
	res, err := d.ML.ImageAudit(c.Context(), map[string]any{
		"image_base64": req.ImageBase64, "known_hashes": req.KnownHashes,
		"ela_threshold": ela, "duplicate_hamming_max": ham,
	})
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "forensics engine: "+err.Error())
	}
	return c.JSON(res)
}
