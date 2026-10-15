// Command api boots the DAPE backend HTTP server.
package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/recover"

	"github.com/peritanix/dape-backend/internal/auth"
	"github.com/peritanix/dape-backend/internal/config"
	"github.com/peritanix/dape-backend/internal/handlers"
	"github.com/peritanix/dape-backend/internal/infra/mlclient"
	"github.com/peritanix/dape-backend/internal/infra/postgres"
	rds "github.com/peritanix/dape-backend/internal/infra/redis"
	"github.com/peritanix/dape-backend/internal/infra/storage"
	"github.com/peritanix/dape-backend/internal/middleware"
	"github.com/peritanix/dape-backend/internal/worker"
)

func main() {
	cfg := config.Load()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// --- Infra ---
	store, err := postgres.New(ctx, cfg.DatabaseURL)
	if err != nil {
		slog.Error("postgres connect failed", "err", err)
		os.Exit(1)
	}
	defer store.Close()

	cache, err := rds.New(ctx, cfg.RedisAddr)
	if err != nil {
		slog.Error("redis connect failed", "err", err)
		os.Exit(1)
	}
	defer cache.Close()

	ml := mlclient.New(cfg.MLBaseURL, cfg.InternalToken)

	// Object storage: HTTP store when an endpoint is configured, else no-op.
	var objStore storage.Store = storage.NewNoopStore(cfg.MinioPublic)
	if cfg.MinioEndpoint != "" {
		objStore = storage.NewHTTPStore("http://"+cfg.MinioEndpoint, cfg.MinioBucket)
	}

	// --- HTTP app ---
	app := fiber.New(fiber.Config{
		AppName:               "DAPE API",
		DisableStartupMessage: true,
	})

	app.Use(recover.New())
	// Structured JSON logging with request-id correlation. In development a
	// human-readable handler is used; in production a JSON handler so logs are
	// machine-parseable (ELK/Loki).
	if cfg.Env == "production" {
		slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
			Level: slog.LevelInfo,
		})))
	} else {
		slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
			Level: slog.LevelInfo,
		})))
	}
	app.Use(func(c *fiber.Ctx) error {
		start := time.Now()
		err := c.Next()
		slog.Info("http",
			"request_id", middleware.GetRequestID(c),
			"method", c.Method(),
			"path", c.Path(),
			"status", c.Response().StatusCode(),
			"latency_ms", time.Since(start).Milliseconds(),
			"ip", c.IP(),
		)
		return err
	})
	app.Use(cors.New(cors.Config{
		AllowOrigins: "*",
		AllowHeaders: "Origin, Content-Type, Accept, Authorization, X-Internal-Token",
		AllowMethods: "GET, POST, PUT, DELETE, OPTIONS",
	}))

	app.Get("/healthz", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})

	deps := &handlers.Deps{
		Cfg:      cfg,
		Lands:    postgres.NewLandRepo(store),
		Users:    postgres.NewUserRepo(store),
		Soil:     postgres.NewSoilRepo(store),
		Disease:  postgres.NewDiseaseRepo(store),
		Harvest:  postgres.NewHarvestRepo(store),
		ML:       ml,
		Cache:    cache,
		Store:    store,
		Auth:     auth.NewIssuer(cfg.JWTSecret, cfg.JWTTTLHours),
		Storage:  objStore,
		GDD:      postgres.NewGDDRepo(store),
		Feedback: postgres.NewFeedbackRepo(store),
		Climate:  postgres.NewClimateRepo(store),
	}
	handlers.Register(app, deps)

	// --- Background workers ---
	worker.StartGDDCron(cfg, deps)
	worker.StartSyncProcessor(deps)

	// --- Serve ---
	go func() {
		if err := app.Listen(":" + cfg.HTTPPort); err != nil {
			slog.Error("listen failed", "err", err)
			os.Exit(1)
		}
	}()
	slog.Info("api listening", "port", cfg.HTTPPort, "env", cfg.Env)

	// --- Graceful shutdown ---
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	slog.Info("shutting down")
	_ = app.ShutdownWithTimeout(10 * time.Second)
}
