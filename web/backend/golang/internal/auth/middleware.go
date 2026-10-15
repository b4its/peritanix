package auth

import (
	"strings"

	"github.com/gofiber/fiber/v2"
)

// Middleware returns a Fiber handler that requires a valid Bearer token.
// It stores the parsed claims in Locals("claims") for downstream handlers.
func (i *Issuer) Middleware() fiber.Handler {
	return func(c *fiber.Ctx) error {
		h := c.Get("Authorization")
		if !strings.HasPrefix(h, "Bearer ") {
			return fiber.NewError(fiber.StatusUnauthorized, "missing bearer token")
		}
		token := strings.TrimPrefix(h, "Bearer ")
		claims, err := i.Parse(token)
		if err != nil {
			return fiber.NewError(fiber.StatusUnauthorized, "invalid token: "+err.Error())
		}
		c.Locals("claims", claims)
		return c.Next()
	}
}

// RequireRole returns middleware that additionally checks the role claim.
func RequireRole(roles ...string) fiber.Handler {
	allowed := make(map[string]struct{}, len(roles))
	for _, r := range roles {
		allowed[r] = struct{}{}
	}
	return func(c *fiber.Ctx) error {
		claims, ok := c.Locals("claims").(*Claims)
		if !ok {
			return fiber.NewError(fiber.StatusUnauthorized, "no claims")
		}
		if _, ok := allowed[claims.Role]; !ok {
			return fiber.NewError(fiber.StatusForbidden, "role not permitted")
		}
		return c.Next()
	}
}

// ClaimsFrom extracts the claims previously stored by Middleware.
func ClaimsFrom(c *fiber.Ctx) (*Claims, bool) {
	claims, ok := c.Locals("claims").(*Claims)
	return claims, ok
}
