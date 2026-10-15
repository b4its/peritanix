package handlers

import (
	"encoding/json"
	"fmt"
	"log"
	"strconv"
	"strings"

	"github.com/gofiber/fiber/v2"
	"github.com/peritanix/dape-backend/internal/domain"
	"github.com/peritanix/dape-backend/internal/infra/mlclient"
)

type landInitReq struct {
	UserID           string    `json:"user_id"`
	Name             string    `json:"name"`
	BoundaryWKT      string    `json:"boundary_wkt"`
	ElevationM       float64   `json:"elevation_m"`
	PlantingDate     string    `json:"planting_date"`
	Ed25519PublicKey string    `json:"ed25519_public_key"`
	WhiteReference   []float64 `json:"white_reference"`
}

type landInitResp struct {
	Land     *domain.Land  `json:"land"`
	TWI      map[string]any `json:"twi_cache"`
	CacheKB  float64       `json:"cache_kb"`
}

// landInit creates a plot, registers the key, and fetches the TWI/flow cache.
func (d *Deps) landInit(c *fiber.Ctx) error {
	var req landInitReq
	if err := c.BodyParser(&req); err != nil {
		return fiber.NewError(fiber.StatusBadRequest, "invalid JSON body")
	}
	if req.UserID == "" || req.BoundaryWKT == "" {
		return fiber.NewError(fiber.StatusBadRequest, "user_id and boundary_wkt are required")
	}

	// Register / update the farmer's public key & white reference if provided.
	if req.Ed25519PublicKey != "" {
		if u, err := d.Users.GetByID(c.Context(), req.UserID); err == nil {
			u.Ed25519PublicKey = req.Ed25519PublicKey
			if len(req.WhiteReference) > 0 {
				u.WhiteReference = req.WhiteReference
			}
		}
	}

	land := &domain.Land{
		UserID:         req.UserID,
		Name:           req.Name,
		BoundaryWKT:    req.BoundaryWKT,
		ElevationM:     req.ElevationM,
		PlantingDate:   req.PlantingDate,
		AccumulatedGDD: 0,
		CurrentPhase:   domain.PhaseVegetative,
		UpdatedTS:      nowTS(),
	}
	if err := d.Lands.Create(c.Context(), land); err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}

	// Derive the bounding box of the WKT polygon (simple parse) and fetch TWI.
	twi := map[string]any{}
	minLon, minLat, maxLon, maxLat, ok := parseWKTBounds(req.BoundaryWKT)
	if ok {
		box := mlclient.BBox{LandID: land.ID, MinLon: minLon, MinLat: minLat,
			MaxLon: maxLon, MaxLat: maxLat}
		if res, err := d.ML.ExtractDEM(c.Context(), box); err == nil {
			twi = res
			if kb, ok := res["cache_kb"].(float64); ok {
				_ = d.Cache.Set(c.Context(), "twi:"+land.ID,
					fmt.Sprintf("%.1f", kb), 0)
			}
		} else {
			log.Printf("[land-init] extract-dem for %s failed: %v", land.ID, err)
		}
	} else {
		log.Printf("[land-init] could not parse WKT bounds for %s", land.ID)
	}

	kb, _ := twi["cache_kb"].(float64)
	return c.Status(fiber.StatusCreated).JSON(landInitResp{Land: land, TWI: twi, CacheKB: kb})
}

func (d *Deps) landList(c *fiber.Ctx) error {
	lands, err := d.Lands.ListAll(c.Context())
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	return c.JSON(fiber.Map{"lands": lands})
}

func (d *Deps) landGet(c *fiber.Ctx) error {
	l, err := d.Lands.GetByID(c.Context(), c.Params("id"))
	if err != nil {
		return fiber.NewError(fiber.StatusNotFound, "land not found")
	}
	return c.JSON(l)
}

func (d *Deps) landGeoJSON(c *fiber.Ctx) error {
	js, err := d.Lands.ListGeoJSON(c.Context())
	if err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	c.Set("Content-Type", "application/geo+json")
	return c.Send(js)
}

type gddReq struct {
	Series   [][3]any `json:"series"` // [day, t_min, t_max]
	StartCum float64  `json:"start_cum"`
}

// landRecomputeGDD calls the ML GDD engine and persists the new phase.
func (d *Deps) landRecomputeGDD(c *fiber.Ctx) error {
	landID := c.Params("id")
	land, err := d.Lands.GetByID(c.Context(), landID)
	if err != nil {
		return fiber.NewError(fiber.StatusNotFound, "land not found")
	}
	var req gddReq
	_ = c.BodyParser(&req)

	// Build a series; if none provided use a synthetic warm-season series.
	series := req.Series
	if len(series) == 0 {
		for i := 0; i < 14; i++ {
			series = append(series, [3]any{
				fmt.Sprintf("d%02d", i), 20.0 + float64(i%3), 30.0 + float64(i%4),
			})
		}
	}
	in := map[string]any{
		"land_id":   landID,
		"series":    series,
		"start_cum": land.AccumulatedGDD,
	}
	res, err := d.ML.GDD(c.Context(), in)
	if err != nil {
		return fiber.NewError(fiber.StatusBadGateway, "gdd engine: "+err.Error())
	}
	gdd, _ := res["accumulated_gdd"].(float64)
	phase, _ := res["current_phase"].(string)
	if err := d.Lands.UpdateGDD(c.Context(), landID, gdd, domain.LandPhase(phase)); err != nil {
		return fiber.NewError(fiber.StatusInternalServerError, err.Error())
	}
	return c.JSON(res)
}

// parseWKTBounds extracts a bbox from "POLYGON((lon lat, lon lat, ...))".
func parseWKTBounds(wkt string) (minLon, minLat, maxLon, maxLat float64, ok bool) {
	open := strings.Index(wkt, "((")
	close := strings.LastIndex(wkt, "))")
	if open < 0 || close < 0 || close <= open {
		return 0, 0, 0, 0, false
	}
	inner := wkt[open+2 : close]
	coords := strings.Split(inner, ",")
	minLon, minLat = 1e18, 1e18
	maxLon, maxLat = -1e18, -1e18
	for _, c := range coords {
		fields := strings.Fields(strings.TrimSpace(c))
		if len(fields) < 2 {
			continue
		}
		lon, e1 := strconv.ParseFloat(fields[0], 64)
		lat, e2 := strconv.ParseFloat(fields[1], 64)
		if e1 != nil || e2 != nil {
			continue
		}
		if lon < minLon {
			minLon = lon
		}
		if lon > maxLon {
			maxLon = lon
		}
		if lat < minLat {
			minLat = lat
		}
		if lat > maxLat {
			maxLat = lat
		}
		ok = true
	}
	return
}

// marshalAny is a small helper kept for future use.
func marshalAny(v any) string {
	b, _ := json.Marshal(v)
	return string(b)
}
