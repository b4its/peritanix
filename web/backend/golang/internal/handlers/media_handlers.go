package handlers

import (
	"encoding/base64"
	"fmt"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
)

// --- Media upload -----------------------------------------------------------

type mediaUploadReq struct {
	LandID      string `json:"land_id"`
	Kind        string `json:"kind"` // burst | trans_illum | ooze | twi_raster | archimedes | photometry
	Filename    string `json:"filename"`
	ContentType string `json:"content_type"`
	// ContentBase64 carries the bytes as base64 so the payload survives the
	// offline sync queue as plain JSON (no multipart assembly on-device).
	ContentBase64 string `json:"content_base64"`
}

var allowedKinds = map[string]bool{
	"burst": true, "trans_illum": true, "ooze": true, "twi_raster": true,
	"archimedes": true, "photometry": true,
}

// mediaUpload stores a field-media object under dape-raw and returns its URL.
func (d *Deps) mediaUpload(c *fiber.Ctx) error {
	var req mediaUploadReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.LandID == "" || req.Kind == "" || req.ContentBase64 == "" {
		return fiber.NewError(fiber.StatusBadRequest, "land_id, kind and content_base64 required")
	}
	if !allowedKinds[req.Kind] {
		return fiber.NewError(fiber.StatusBadRequest, "unsupported media kind")
	}
	if d.Storage == nil {
		return fiber.NewError(fiber.StatusServiceUnavailable, "storage not configured")
	}
	raw, err := base64.StdEncoding.DecodeString(req.ContentBase64)
	if err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid base64 content")
	}
	// Guard against oversized uploads (10 MB cap).
	if len(raw) > 10*1024*1024 {
		return fiber.NewError(fiber.StatusRequestEntityTooLarge, "media exceeds 10 MB")
	}

	fname := safeFilename(req.Filename)
	if fname == "" {
		fname = fmt.Sprintf("%d", time.Now().UnixNano())
	}
	key := fmt.Sprintf("lands/%s/%s/%s", req.LandID, req.Kind, fname)

	url, err := d.Storage.Put(c.Context(), key, raw, req.ContentType)
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "storage error: "+err.Error())
	}
	// Cache the URL mapping for later reads.
	_ = d.Cache.Set(c.Context(), "media:"+key, url, 0)

	return c.Status(fiber.StatusCreated).JSON(fiber.Map{
		"key":     key,
		"url":     url,
		"bytes":   len(raw),
		"land_id": req.LandID,
		"kind":    req.Kind,
	})
}

// safeFilename strips path components and suspicious characters.
func safeFilename(s string) string {
	s = strings.TrimSpace(s)
	if s == "" {
		return ""
	}
	// Drop any directory component.
	if i := strings.LastIndexAny(s, "/\\"); i >= 0 {
		s = s[i+1:]
	}
	var b strings.Builder
	for _, r := range s {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') ||
			(r >= '0' && r <= '9') || r == '.' || r == '-' || r == '_' {
			b.WriteRune(r)
		}
	}
	out := b.String()
	if len(out) > 120 {
		out = out[len(out)-120:]
	}
	// Reject "..", "." and empty.
	if out == "." || out == ".." {
		return ""
	}
	return out
}
