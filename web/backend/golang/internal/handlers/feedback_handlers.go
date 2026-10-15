package handlers

import (
	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/domain"
)

// --- Continuous-learning feedback -------------------------------------------

// retrainThresholds: minimum labelled examples before a model is worth retraining.
var retrainThresholds = map[string]int64{
	"detector_yolov8n":       200,
	"classifier_mobilenetv3": 150,
	"severity_unet_lite":     150,
	"fertilizer_lightgbm":    300,
}

type feedbackReq struct {
	ModelName   string         `json:"model_name"`
	Inputs      map[string]any `json:"inputs"`
	Predicted   map[string]any `json:"predicted"`
	Corrected   map[string]any `json:"corrected"`
	LandID      string         `json:"land_id"`
	DiagnosisID string         `json:"diagnosis_id"`
	Source      string         `json:"source"`
	Confidence  float64        `json:"confidence"`
}

// feedbackCapture stores a ground-truth correction (e.g. a lab confirmation that
// overrides the model's field verdict) for the next retraining cycle.
func (d *Deps) feedbackCapture(c *fiber.Ctx) error {
	var req feedbackReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.ModelName == "" {
		return fiber.NewError(fiber.StatusBadRequest, "model_name required")
	}
	if req.Source == "" {
		req.Source = "field"
	}
	f := &domain.Feedback{
		ModelName: req.ModelName, Inputs: req.Inputs, Predicted: req.Predicted,
		Corrected: req.Corrected, LandID: req.LandID, DiagnosisID: req.DiagnosisID,
		Source: req.Source, Confidence: req.Confidence,
	}
	id, err := d.Feedback.Create(c.Context(), f)
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	return c.Status(fiber.StatusCreated).JSON(fiber.Map{"id": id, "model_name": req.ModelName})
}

// retrainReadiness reports, per model, how many labelled examples exist and
// whether the threshold for a retraining run is met.
func (d *Deps) retrainReadiness(c *fiber.Ctx) error {
	counts, err := d.Feedback.Counts(c.Context())
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	models := []fiber.Map{}
	for name, threshold := range retrainThresholds {
		n := counts[name]
		models = append(models, fiber.Map{
			"model_name":       name,
			"examples":         n,
			"threshold":        threshold,
			"ready_to_retrain": n >= threshold,
		})
	}
	return c.JSON(fiber.Map{
		"models": models,
		"total":  len(counts),
	})
}
