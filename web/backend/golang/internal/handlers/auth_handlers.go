package handlers

import (
	"encoding/json"
	"strings"

	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/auth"
)

type registerReq struct {
	Name     string `json:"name"`
	Phone    string `json:"phone"`
	Role     string `json:"role"`
	Password string `json:"password"`
}

type authResp struct {
	Token string      `json:"token"`
	User  interface{} `json:"user"`
}

var validRoles = map[string]bool{
	"farmer": true, "worker": true, "agronomist": true, "koperasi": true, "offtaker": true,
}

// register creates a user account and returns a JWT.
func (d *Deps) register(c *fiber.Ctx) error {
	var req registerReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if strings.TrimSpace(req.Name) == "" || strings.TrimSpace(req.Phone) == "" {
		return fiber.NewError(fiber.StatusBadRequest, "name and phone are required")
	}
	if len(req.Password) < 6 {
		return fiber.NewError(fiber.StatusBadRequest, "password must be at least 6 characters")
	}
	if req.Role == "" {
		req.Role = "farmer"
	}
	if !validRoles[req.Role] {
		return fiber.NewError(fiber.StatusBadRequest, "invalid role")
	}
	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, "hash error")
	}
	u, err := d.Users.Register(c.Context(), req.Name, req.Phone, req.Role, hash)
	if err != nil {
		if strings.Contains(err.Error(), "duplicate") || strings.Contains(err.Error(), "unique") {
			return fiber.NewError(fiber.StatusConflict, "phone already registered")
		}
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	token, err := d.Auth.Mint(u.ID, u.Role, "")
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, "token error")
	}
	return c.Status(fiber.StatusCreated).JSON(authResp{Token: token, User: u})
}

type loginReq struct {
	Phone    string `json:"phone"`
	Password string `json:"password"`
}

// login verifies credentials and returns a JWT.
func (d *Deps) login(c *fiber.Ctx) error {
	var req loginReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	id, hash, role, err := d.Users.GetCredential(c.Context(), req.Phone)
	if err != nil || hash == "" || !auth.CheckPassword(hash, req.Password) {
		// Constant-ish message: do not reveal whether the phone exists.
		return fiber.NewError(fiber.StatusUnauthorized, "invalid phone or password")
	}
	token, err := d.Auth.Mint(id, role, "")
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, "token error")
	}
	u, _ := d.Users.GetByID(c.Context(), id)
	return c.JSON(authResp{Token: token, User: u})
}

// me returns the authenticated user.
func (d *Deps) me(c *fiber.Ctx) error {
	claims, ok := auth.ClaimsFrom(c)
	if !ok {
		return fiber.NewError(fiber.StatusUnauthorized, "no claims")
	}
	u, err := d.Users.GetByID(c.Context(), claims.UserID)
	if err != nil {
		return fiber.NewError(fiber.StatusNotFound, "user not found")
	}
	return c.JSON(u)
}

// registerKey stores the farmer's Ed25519 public key and optional white
// reference matrix. Called by the mobile app once the device key pair is
// generated, and again after spectral calibration so the buyer QR verifier and
// the CIELAB calibrator can use them. Requires JWT auth.
type registerKeyReq struct {
	Ed25519PublicKey string    `json:"ed25519_public_key"`
	WhiteReference   []float64 `json:"white_reference"`
}

func (d *Deps) registerKey(c *fiber.Ctx) error {
	claims, ok := auth.ClaimsFrom(c)
	if !ok {
		return fiber.NewError(fiber.StatusUnauthorized, "no claims")
	}
	var req registerKeyReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	key := strings.TrimSpace(req.Ed25519PublicKey)
	if key == "" {
		return fiber.NewError(fiber.StatusBadRequest, "ed25519_public_key is required")
	}
	// Basic sanity: an Ed25519 public key is 32 bytes -> base64 (44 chars) or hex (64).
	if !validEd25519PublicKey(key) {
		return fiber.NewError(fiber.StatusBadRequest, "ed25519_public_key has unexpected length")
	}
	whiteJSON := ""
	if len(req.WhiteReference) == 3 {
		b, _ := json.Marshal(req.WhiteReference)
		whiteJSON = string(b)
	}
	if err := d.Users.SetPublicKey(c.Context(), claims.UserID, key, whiteJSON); err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, "failed to store public key")
	}
	return c.JSON(fiber.Map{"status": "ok", "ed25519_public_key": key})
}

// validEd25519PublicKey accepts a base64 (44 char) or hex (64 char) encoding
// of a 32-byte Ed25519 public key.
func validEd25519PublicKey(k string) bool {
	return len(k) == 44 || len(k) == 64
}
