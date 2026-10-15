// Package handlers wires HTTP routes to domain services (Fiber).
package handlers

import (
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/config"
	"github.com/peritanix/dape-backend/internal/domain"
	"github.com/peritanix/dape-backend/internal/infra/crypto"
	"github.com/peritanix/dape-backend/internal/infra/mlclient"
	"github.com/peritanix/dape-backend/internal/infra/postgres"
	rds "github.com/peritanix/dape-backend/internal/infra/redis"
)

// Deps bundles all handler dependencies.
type Deps struct {
	Cfg      config.Config
	Lands    domain.LandRepo
	Users    domain.UserRepo
	Soil     domain.SoilRepo
	Disease  domain.DiseaseRepo
	Harvest  domain.HarvestRepo
	ML       *mlclient.Client
	Cache    *rds.Client
	Store    *postgres.Store
}

// Register mounts all routes on the app.
func Register(app *fiber.App, d *Deps) {
	api := app.Group("/api/v1")

	api.Get("/health", d.health)
	api.Get("/stats", d.stats)

	// Lands
	api.Post("/lands/init", d.landInit)
	api.Get("/lands", d.landList)
	api.Get("/lands/geojson", d.landGeoJSON)
	api.Get("/lands/:id", d.landGet)
	api.Post("/lands/:id/recompute-gdd", d.landRecomputeGDD)

	// Soil
	api.Post("/soil/sync", d.soilSync)
	api.Get("/lands/:id/soil", d.soilList)

	// Diagnostics
	api.Post("/diagnostics/evaluate", d.diagEvaluate)
	api.Get("/lands/:id/diseases", d.diseaseList)
	api.Get("/lands/:id/diseases/geojson", d.diseaseGeoJSON)

	// Post-harvest
	api.Post("/postharvest/calorimetry", d.calorimetry)
	api.Post("/postharvest/archimedes", d.archimedes)
	api.Post("/harvest/batches", d.harvestCreate)
	api.Get("/harvest/batches/:code", d.harvestGet)

	// Multi-modal optics (burst, trans-illumination, ooze)
	api.Post("/optics/mfsr", d.mfsrStack)
	api.Post("/optics/trans-illumination", d.transIllumination)
	api.Post("/optics/ooze", d.oozeOpticalFlow)

	// Post-harvest grading + microclimate EWS
	api.Post("/postharvest/grade", d.gradeFruit)
	api.Post("/agronomy/ews", d.ewsMicroclimate)

	// Anti-fraud image forensics
	api.Post("/forensics/image-audit", d.imageAudit)

	// Certificates (public verifier — no auth)
	api.Post("/certificates/verify", d.certVerify)

	// Offline sync
	api.Post("/sync/delta", d.syncDelta)
}

func (d *Deps) health(c *fiber.Ctx) error {
	mlOK := d.ML.Health(c.Context()) == nil
	return c.JSON(fiber.Map{
		"status":   "ok",
		"service":  "dape-api",
		"env":      d.Cfg.Env,
		"ml":       mlOK,
		"time":     time.Now().UTC(),
	})
}

func (d *Deps) stats(c *fiber.Ctx) error {
	lands, _ := d.Lands.ListAll(c.Context())
	return c.JSON(fiber.Map{
		"land_count": len(lands),
		"server_ts":  time.Now().Unix(),
	})
}

// --- Certificate verification ----------------------------------------------

type certVerifyReq struct {
	Payload   map[string]any `json:"payload"`
	Signature string         `json:"signature"`
	PublicKey string         `json:"public_key"`
}

func (d *Deps) certVerify(c *fiber.Ctx) error {
	var req certVerifyReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	res := crypto.Verify(crypto.Certificate{
		Payload:   req.Payload,
		Signature: req.Signature,
		PublicKey: req.PublicKey,
	})
	return c.JSON(res)
}
