// Package middleware holds cross-cutting HTTP concerns: request IDs, UUID
// validation, and rate limiting. Kept separate from handlers so they compose.
package middleware

import (
	"crypto/rand"
	"encoding/hex"
	"regexp"
	"strings"

	"github.com/gofiber/fiber/v2"
)

// RequestID assigns a short request id (from an incoming header or generated)
// and echoes it back, so logs and clients can correlate a request.
func RequestID() fiber.Handler {
	const header = "X-Request-ID"
	return func(c *fiber.Ctx) error {
		id := c.Get(header)
		if id == "" || len(id) > 64 {
			id = newID()
		}
		c.Locals("request_id", id)
		c.Set(header, id)
		return c.Next()
	}
}

// newID returns a 16-hex-char random id.
func newID() string {
	var b [8]byte
	_, _ = rand.Read(b[:])
	return hex.EncodeToString(b[:])
}

// GetRequestID returns the id stored by RequestID (or "").
func GetRequestID(c *fiber.Ctx) string {
	if v, ok := c.Locals("request_id").(string); ok {
		return v
	}
	return ""
}

var uuidRe = regexp.MustCompile(
	`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// IsUUID reports whether s is a canonical UUID string.
func IsUUID(s string) bool { return uuidRe.MatchString(s) }

// RequireUUIDParam returns middleware that 400s when the named route param is
// not a valid UUID. Prevents malformed ids from reaching the database layer.
func RequireUUIDParam(param string) fiber.Handler {
	return func(c *fiber.Ctx) error {
		v := c.Params(param)
		if !IsUUID(v) {
			return fiber.NewError(fiber.StatusBadRequest, "invalid "+param+": must be a UUID")
		}
		return c.Next()
	}
}

// TrimTrailingSlash normalises paths so /lands and /lands/ are the same route.
func TrimTrailingSlash() fiber.Handler {
	return func(c *fiber.Ctx) error {
		p := c.Path()
		if len(p) > 1 && strings.HasSuffix(p, "/") {
			c.Path(strings.TrimRight(p, "/"))
		}
		return c.Next()
	}
}
