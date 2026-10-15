package handlers

import (
	"runtime"

	"github.com/gofiber/fiber/v2"
)

// Build information, overridable at link time with
//
//	-ldflags "-X .../handlers.BuildVersion=1.2.3 -X .../handlers.BuildCommit=abc"
var (
	BuildVersion = "1.0.0-dev"
	BuildCommit  = "unknown"
)

// version reports build/runtime info for ops observability.
func (d *Deps) version(c *fiber.Ctx) error {
	return c.JSON(fiber.Map{
		"service":       "dape-api",
		"version":       BuildVersion,
		"commit":        BuildCommit,
		"env":           d.Cfg.Env,
		"go_version":    runtime.Version(),
		"auth_required": d.Cfg.AuthRequired,
	})
}

// modelRuntime proxies the ML service's ONNX runtime status so operators (and
// the web OTA panel) can see which models are loadable vs falling back.
func (d *Deps) modelRuntime(c *fiber.Ctx) error {
	res, err := d.ML.ModelRuntime(c.Context())
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "model runtime: "+err.Error())
	}
	return c.JSON(res)
}
