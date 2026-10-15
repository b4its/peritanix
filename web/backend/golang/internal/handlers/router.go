// Package handlers wires HTTP routes to domain services (Fiber).
package handlers

import (
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/auth"
	"github.com/peritanix/dape-backend/internal/config"
	"github.com/peritanix/dape-backend/internal/domain"
	"github.com/peritanix/dape-backend/internal/infra/crypto"
	"github.com/peritanix/dape-backend/internal/infra/mlclient"
	"github.com/peritanix/dape-backend/internal/infra/postgres"
	rds "github.com/peritanix/dape-backend/internal/infra/redis"
)

// Deps bundles all handler dependencies.
type Deps struct {
	Cfg     config.Config
	Lands   domain.LandRepo
	Users   domain.UserRepo
	Soil    domain.SoilRepo
	Disease domain.DiseaseRepo
	Harvest domain.HarvestRepo
	ML      *mlclient.Client
	Cache   *rds.Client
	Store   *postgres.Store
	Auth    *auth.Issuer
}

// Register mounts all routes on the app.
//
// Public routes: health, auth (register/login), certificate verification and
// the read-only geospatial/GDD endpoints a buyer or dashboard needs. Mutating
// routes are wrapped by the JWT middleware when Cfg.AuthRequired is true
// (default false in development so local scripts run without a token).
func Register(app *fiber.App, d *Deps) {
	api := app.Group("/api/v1")

	// --- Public: meta + auth ------------------------------------------------
	api.Get("/health", d.health)
	api.Get("/stats", d.stats)
	api.Post("/auth/register", d.register)
	api.Post("/auth/login", d.login)
	api.Get("/auth/me", d.Auth.Middleware(), d.me)

	// --- Public reads -------------------------------------------------------
	api.Get("/lands", d.landList)
	api.Get("/lands/geojson", d.landGeoJSON)
	api.Get("/lands/:id", d.landGet)
	api.Get("/lands/:id/soil", d.soilList)
	api.Get("/lands/:id/diseases", d.diseaseList)
	api.Get("/lands/:id/diseases/geojson", d.diseaseGeoJSON)
	api.Get("/harvest/batches/:code", d.harvestGet)
	api.Post("/certificates/verify", d.certVerify) // public verifier — no auth

	// --- Mutating group (guarded when auth is required) ---------------------
	mut := api.Group("")
	if d.Cfg.AuthRequired {
		mut.Use(d.Auth.Middleware())
	}

	// Lands
	mut.Post("/lands/init", d.landInit)
	mut.Post("/lands/:id/recompute-gdd", d.landRecomputeGDD)

	// Soil
	mut.Post("/soil/sync", d.soilSync)

	// Diagnostics
	mut.Post("/diagnostics/evaluate", d.diagEvaluate)

	// Post-harvest
	mut.Post("/postharvest/calorimetry", d.calorimetry)
	mut.Post("/postharvest/archimedes", d.archimedes)
	mut.Post("/harvest/batches", d.harvestCreate)

	// Multi-modal optics (burst, trans-illumination, ooze)
	mut.Post("/optics/mfsr", d.mfsrStack)
	mut.Post("/optics/trans-illumination", d.transIllumination)
	mut.Post("/optics/ooze", d.oozeOpticalFlow)

	// Post-harvest grading + microclimate EWS
	mut.Post("/postharvest/grade", d.gradeFruit)
	mut.Post("/agronomy/ews", d.ewsMicroclimate)

	// Anti-fraud image forensics
	mut.Post("/forensics/image-audit", d.imageAudit)

	// Decision support: yield projection, protocols, trace validation
	mut.Post("/yield/projection", d.yieldProjection)
	mut.Post("/protocols/prescribe", d.protocolPrescribe)
	mut.Post("/certificates/validate-trace", d.validateTrace)
	api.Get("/protocols", d.protocolCatalog) // public catalogue

	// Offline sync
	mut.Post("/sync/delta", d.syncDelta)
	mut.Post("/sync/merge", d.syncMerge)

}

func (d *Deps) health(c *fiber.Ctx) error {
	mlOK := d.ML.Health(c.Context()) == nil
	return c.JSON(fiber.Map{
		"status":  "ok",
		"service": "dape-api",
		"env":     d.Cfg.Env,
		"ml":      mlOK,
		"time":    time.Now().UTC(),
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
