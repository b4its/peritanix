package handlers

import (
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
