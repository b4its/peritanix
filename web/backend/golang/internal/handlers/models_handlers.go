package handlers

import (
	"github.com/gofiber/fiber/v2"
)

// --- Model OTA (Continuous Learning loop) -----------------------------------

type modelManifestReq struct {
	ClientVersions map[string]string `json:"client_versions"`
	BaseURL        string            `json:"base_url"`
}

// modelManifest returns the OTA manifest: only models whose version changed
// since the client's last-known set are flagged (minimum-data downloads).
func (d *Deps) modelManifest(c *fiber.Ctx) error {
	var req modelManifestReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.ClientVersions == nil {
		req.ClientVersions = map[string]string{}
	}
	baseURL := req.BaseURL
	if baseURL == "" {
		baseURL = d.Cfg.MinioPublic + "/models"
	}
	res, err := d.ML.ModelManifest(c.Context(), req.ClientVersions, baseURL)
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "model registry: "+err.Error())
	}
	return c.JSON(res)
}

// modelList returns all registered models (public read).
func (d *Deps) modelList(c *fiber.Ctx) error {
	res, err := d.ML.ModelList(c.Context())
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "model registry: "+err.Error())
	}
	return c.JSON(res)
}
