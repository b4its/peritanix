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
	"github.com/peritanix/dape-backend/internal/infra/storage"
	"github.com/peritanix/dape-backend/internal/middleware"
)

// Deps bundles all handler dependencies.
type Deps struct {
	Cfg      config.Config
	Lands    domain.LandRepo
	Users    domain.UserRepo
	Soil     domain.SoilRepo
	Disease  domain.DiseaseRepo
	Harvest  domain.HarvestRepo
	GDD      domain.GDDRepo
	Feedback domain.FeedbackRepo
	Climate  domain.ClimateRepo
	ML       *mlclient.Client
	Cache    *rds.Client
	Store    *postgres.Store
	Auth     *auth.Issuer
	Storage  storage.Store
}

// Register mounts all routes on the app.
//
// Public routes: health, auth (register/login), certificate verification and
// the read-only geospatial/GDD endpoints a buyer or dashboard needs. Mutating
// routes are wrapped by the JWT middleware when Cfg.AuthRequired is true
// (default false in development so local scripts run without a token).
func Register(app *fiber.App, d *Deps) {
	// Cross-cutting middleware: request id + trailing-slash normalisation.
	app.Use(middleware.RequestID())
	app.Use(middleware.TrimTrailingSlash())

	api := app.Group("/api/v1")

	// Auth rate limiter (brute-force protection).
	authLimiter := middleware.NewRateLimiter(20, time.Minute)
	stopSweep := make(chan struct{})
	authLimiter.StartSweeper(5*time.Minute, stopSweep)

	// --- Public: meta + auth ------------------------------------------------
	api.Get("/health", d.health)
	api.Get("/readyz", d.readyz)
	api.Get("/version", d.version)
	api.Get("/stats", d.stats)
	api.Post("/auth/register", authLimiter.Middleware(), d.register)
	api.Post("/auth/login", authLimiter.Middleware(), d.login)
	api.Get("/auth/me", d.Auth.Middleware(), d.me)

	// --- Public reads -------------------------------------------------------
	api.Get("/lands", d.landList)
	api.Get("/lands/geojson", d.landGeoJSON)
	api.Get("/soil/fertility/geojson", d.soilFertilityGeoJSON)
	api.Get("/protocols", d.protocolCatalog)
	api.Get("/models", d.modelList)
	api.Get("/lands/:id", middleware.RequireUUIDParam("id"), d.landGet)
	api.Get("/lands/:id/soil", middleware.RequireUUIDParam("id"), d.soilList)
	api.Get("/lands/:id/diseases", middleware.RequireUUIDParam("id"), d.diseaseList)
	api.Get("/lands/:id/diseases/geojson", middleware.RequireUUIDParam("id"), d.diseaseGeoJSON)
	api.Get("/lands/:id/gdd", middleware.RequireUUIDParam("id"), d.gddHistory)
	api.Get("/lands/:id/twi", middleware.RequireUUIDParam("id"), d.landTWI)
	api.Get("/harvest/batches/:code", d.harvestGet)
	api.Post("/certificates/verify", d.certVerify) // public verifier — no auth

	// --- Mutating group (guarded when auth is required) ---------------------
	mut := api.Group("")
	if d.Cfg.AuthRequired {
		mut.Use(d.Auth.Middleware())
	}

	// Lands
	mut.Post("/lands/init", d.landInit)
	mut.Post("/lands/:id/recompute-gdd", middleware.RequireUUIDParam("id"), d.landRecomputeGDD)

	// Soil
	mut.Post("/soil/sync", d.soilSync)

	// Fortification Lock protocol guard (block high-N during anthesis)
	mut.Post("/fertilizer/guard", d.fortificationGuard)
	// Prescriptive fertilizer calculator (exact kocor gram doses)
	api.Post("/fertilizer/plan", d.fertilizerPlan)
	// Diagnostic protocol rules (e.g. mandatory ooze test)
	api.Post("/protocols/rules", d.protocolRules)

	// Diagnostics
	mut.Post("/diagnostics/evaluate", d.diagEvaluate)

	// Post-harvest
	mut.Post("/postharvest/calorimetry", d.calorimetry)
	mut.Post("/postharvest/archimedes", d.archimedes)
	mut.Post("/harvest/batches", d.harvestCreate)

	// Multi-modal optics (burst, trans-illumination, ooze, severity)
	mut.Post("/optics/mfsr", d.mfsrStack)
	mut.Post("/optics/trans-illumination", d.transIllumination)
	mut.Post("/optics/ooze", d.oozeOpticalFlow)
	mut.Post("/optics/severity", d.severity)

	// Post-harvest grading + microclimate EWS
	mut.Post("/postharvest/grade", d.gradeFruit)
	mut.Post("/agronomy/ews", d.ewsMicroclimate)

	// Anti-fraud image forensics
	mut.Post("/forensics/image-audit", d.imageAudit)

	// Decision support: yield projection, protocols, trace validation
	mut.Post("/yield/projection", d.yieldProjection)
	mut.Post("/yield/fruit-count", d.fruitCount)
	mut.Post("/protocols/prescribe", d.protocolPrescribe)
	mut.Post("/certificates/validate-trace", d.validateTrace)
	mut.Post("/postharvest/decision", d.harvestDecision)

	// Media upload (burst, trans-illum, ooze, rasters)
	mut.Post("/media/upload", d.mediaUpload)

	// Model OTA (Continuous Learning loop): manifest
	mut.Post("/models/manifest", d.modelManifest)
	api.Get("/models/runtime", d.modelRuntime)

	// Continuous learning: capture ground-truth feedback + retrain readiness
	mut.Post("/ml/feedback", d.feedbackCapture)
	api.Get("/ml/retrain-readiness", d.retrainReadiness)

	// Microclimate sensor ingest (ESP32/BLE logger) + EWS
	mut.Post("/climate/ingest", d.climateIngest)
	api.Get("/lands/:id/ews", middleware.RequireUUIDParam("id"), d.climateEws)

	// Economic impact
	mut.Post("/economics", d.economics)

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

// readyz is a deep readiness probe: it verifies every downstream dependency
// (Postgres, Redis, ML) and returns 503 if any is down, so Kubernetes/ingress
// does not route traffic to an instance that cannot serve.
func (d *Deps) readyz(c *fiber.Ctx) error {
	ctx := c.Context()
	checks := map[string]bool{}

	// Postgres: a trivial round-trip.
	pgOK := false
	if d.Store != nil && d.Store.Pool != nil {
		pgOK = d.Store.Pool.Ping(ctx) == nil
	}
	checks["postgres"] = pgOK

	// Redis: PING.
	redisOK := false
	if d.Cache != nil && d.Cache.RDB != nil {
		redisOK = d.Cache.RDB.Ping(ctx).Err() == nil
	}
	checks["redis"] = redisOK

	// ML engine.
	checks["ml"] = d.ML.Health(ctx) == nil

	allOK := pgOK && redisOK
	status := fiber.StatusOK
	overall := "ready"
	if !allOK {
		status = fiber.StatusServiceUnavailable
		overall = "not_ready"
	}
	return c.Status(status).JSON(fiber.Map{
		"status": overall,
		"checks": checks,
		"time":   time.Now().UTC(),
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
	// Optional: when set (and public_key is empty), verify against the farmer's
	// key stored server-side — a stronger check than trusting the scanned key.
	UserID string `json:"user_id"`
}

func (d *Deps) certVerify(c *fiber.Ctx) error {
	var req certVerifyReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}

	pubKey := req.PublicKey
	// If no key is embedded, fall back to the farmer's registered key.
	if pubKey == "" && req.UserID != "" && middleware.IsUUID(req.UserID) {
		if u, err := d.Users.GetByID(c.Context(), req.UserID); err == nil {
			pubKey = u.Ed25519PublicKey
		}
	}

	res := crypto.Verify(crypto.Certificate{
		Payload:   req.Payload,
		Signature: req.Signature,
		PublicKey: pubKey,
	})
	// If the scanned payload itself carried a key AND we have a stored one,
	// require they match (prevents swapping in a forged key).
	if res.Valid && req.PublicKey != "" && req.UserID != "" {
		if u, err := d.Users.GetByID(c.Context(), req.UserID); err == nil {
			if u.Ed25519PublicKey != "" && u.Ed25519PublicKey != req.PublicKey {
				return c.JSON(fiber.Map{
					"valid":  false,
					"reason": "scanned public key does not match the registered farmer key",
				})
			}
		}
	}
	return c.JSON(res)
}
