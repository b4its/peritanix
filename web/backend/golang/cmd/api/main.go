// Command api boots the DAPE backend HTTP server.
package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"

	"github.com/peritanix/dape-backend/internal/auth"
	"github.com/peritanix/dape-backend/internal/config"
	"github.com/peritanix/dape-backend/internal/handlers"
	"github.com/peritanix/dape-backend/internal/infra/mlclient"
	"github.com/peritanix/dape-backend/internal/infra/postgres"
	rds "github.com/peritanix/dape-backend/internal/infra/redis"
	"github.com/peritanix/dape-backend/internal/worker"
)

func main() {
	cfg := config.Load()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// --- Infra ---
	store, err := postgres.New(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("postgres: %v", err)
	}
	defer store.Close()

	cache, err := rds.New(ctx, cfg.RedisAddr)
	if err != nil {
		log.Fatalf("redis: %v", err)
	}
	defer cache.Close()

	ml := mlclient.New(cfg.MLBaseURL, cfg.InternalToken)

	// --- HTTP app ---
	app := fiber.New(fiber.Config{
		AppName:               "DAPE API",
		DisableStartupMessage: true,
	})

	app.Use(recover.New())
	app.Use(logger.New(logger.Config{
		Format:     "[api] ${time} ${status} ${latency} ${method} ${path}\n",
		TimeFormat: time.RFC3339,
	}))
	app.Use(cors.New(cors.Config{
		AllowOrigins: "*",
		AllowHeaders: "Origin, Content-Type, Accept, Authorization, X-Internal-Token",
		AllowMethods: "GET, POST, PUT, DELETE, OPTIONS",
	}))

	app.Get("/healthz", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})

	deps := &handlers.Deps{
		Cfg:     cfg,
		Lands:   postgres.NewLandRepo(store),
		Users:   postgres.NewUserRepo(store),
		Soil:    postgres.NewSoilRepo(store),
		Disease: postgres.NewDiseaseRepo(store),
		Harvest: postgres.NewHarvestRepo(store),
		ML:      ml,
		Cache:   cache,
		Store:   store,
		Auth:    auth.NewIssuer(cfg.JWTSecret, cfg.JWTTTLHours),
	}
	handlers.Register(app, deps)

	// --- Background GDD cron ---
	worker.StartGDDCron(cfg, deps)

	// --- Serve ---
	go func() {
		if err := app.Listen(":" + cfg.HTTPPort); err != nil {
			log.Fatalf("listen: %v", err)
		}
	}()
	log.Printf("[api] DAPE backend listening on :%s (env=%s)", cfg.HTTPPort, cfg.Env)

	// --- Graceful shutdown ---
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("[api] shutting down...")
	_ = app.ShutdownWithTimeout(10 * time.Second)
}
