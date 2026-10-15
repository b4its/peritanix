// Package mlclient is a typed HTTP client for the FastAPI physics/ML service
// with a lightweight circuit breaker.
package mlclient

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"sync"
	"time"
)

// Client calls the internal FastAPI service.
type Client struct {
	baseURL string
	token   string
	http    *http.Client

	mu        sync.Mutex
	failures  int
	openUntil time.Time
}

// New constructs an ML client.
func New(baseURL, token string) *Client {
	return &Client{
		baseURL: baseURL,
		token:   token,
		http:    &http.Client{Timeout: 60 * time.Second},
	}
}

const (
	failThreshold = 5
	openCooldown  = 15 * time.Second
)

// breakerOpen reports whether the circuit is currently open.
func (c *Client) breakerOpen() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return time.Now().Before(c.openUntil)
}

func (c *Client) record(success bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if success {
		c.failures = 0
		c.openUntil = time.Time{}
		return
	}
	c.failures++
	if c.failures >= failThreshold {
		c.openUntil = time.Now().Add(openCooldown)
	}
}

// post sends JSON and decodes the response into out.
func (c *Client) post(ctx context.Context, path string, in, out any) error {
	if c.breakerOpen() {
		return fmt.Errorf("ml client circuit open")
	}
	body, err := json.Marshal(in)
	if err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+path, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-Token", c.token)

	resp, err := c.http.Do(req)
	if err != nil {
		c.record(false)
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 500 {
		c.record(false)
		b, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("ml %s: status %d: %s", path, resp.StatusCode, string(b))
	}
	c.record(true)
	if resp.StatusCode >= 400 {
		b, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("ml %s: status %d: %s", path, resp.StatusCode, string(b))
	}
	if out == nil {
		return nil
	}
	return json.NewDecoder(resp.Body).Decode(out)
}

// --- Typed operations -------------------------------------------------------

// BBox is a geospatial bounding box request.
type BBox struct {
	LandID string  `json:"land_id"`
	MinLon float64 `json:"min_lon"`
	MinLat float64 `json:"min_lat"`
	MaxLon float64 `json:"max_lon"`
	MaxLat float64 `json:"max_lat"`
}

// ExtractDEM requests a TWI raster package for a bounding box.
func (c *Client) ExtractDEM(ctx context.Context, b BBox) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/gis/extract-dem", b, &out)
	return out, err
}

// SoilCalibrate requests Stokes texture + CEC from measured layer thicknesses.
func (c *Client) SoilCalibrate(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/physics/soil-calibrate", in, &out)
	return out, err
}

// Photometry runs dark-chamber CIELAB matching to a reagent calibration curve.
func (c *Client) Photometry(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/physics/photometry", in, &out)
	return out, err
}

// BayesianDiagnosis runs the multi-modal fusion.
func (c *Client) BayesianDiagnosis(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/ml/bayesian-diagnosis", in, &out)
	return out, err
}

// GDD computes Growing Degree Day accumulation.
func (c *Client) GDD(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/agronomy/gdd", in, &out)
	return out, err
}

// Calorimetry computes the Black's-law hot-water ratio.
func (c *Client) Calorimetry(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/postharvest/calorimetry", in, &out)
	return out, err
}

// Archimedes computes the sink-ratio quality grade.
func (c *Client) Archimedes(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/postharvest/archimedes", in, &out)
	return out, err
}

// FertilizerPlan returns the prescriptive fertilizer mix.
func (c *Client) FertilizerPlan(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/physics/fertilizer-plan", in, &out)
	return out, err
}

// MfsrStack runs multi-frame super-resolution burst stacking.
func (c *Client) MfsrStack(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/optics/mfsr-stack", in, &out)
	return out, err
}

// TransIllumination analyses mesophyll attenuation from a contact-flash image.
func (c *Client) TransIllumination(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/optics/trans-illumination", in, &out)
	return out, err
}

// OozeOpticalFlow distinguishes Ralstonia vs Fusarium from a stem-dip video.
func (c *Client) OozeOpticalFlow(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/optics/ooze-optical-flow", in, &out)
	return out, err
}

// GradeFruit scores a harvested fruit (curvature/colour/defect).
func (c *Client) GradeFruit(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/agronomy/grade-fruit", in, &out)
	return out, err
}

// EwsMicroclimate returns the 72h fungal-blast early warning.
func (c *Client) EwsMicroclimate(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/agronomy/ews-microclimate", in, &out)
	return out, err
}

// ImageAudit runs anti-fraud forensics on a base64 image.
func (c *Client) ImageAudit(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/forensics/image-audit", in, &out)
	return out, err
}

// YieldProjection estimates harvestable mass over the horizon.
func (c *Client) YieldProjection(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/decision/yield-projection", in, &out)
	return out, err
}

// ProtocolPrescribe returns an actionable agrochemical protocol.
func (c *Client) ProtocolPrescribe(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/decision/protocol", in, &out)
	return out, err
}

// ValidateTrace checks an immersion gyroscope trace.
func (c *Client) ValidateTrace(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/decision/validate-trace", in, &out)
	return out, err
}

// HarvestDecision fuses post-harvest physics into a sell/hold recommendation.
func (c *Client) HarvestDecision(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/decision/harvest-decision", in, &out)
	return out, err
}

// Economics computes the farm-level economic impact.
func (c *Client) Economics(ctx context.Context, in map[string]any) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/decision/economics", in, &out)
	return out, err
}

// ProtocolCatalog returns the full pesticide protocol catalogue.
func (c *Client) ProtocolCatalog(ctx context.Context) (map[string]any, error) {
	var out map[string]any
	// GET request
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		c.baseURL+"/internal/decision/protocols", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-Internal-Token", c.token)
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return nil, err
	}
	return out, nil
}

// ModelManifest returns the OTA model manifest for a client's version set.
func (c *Client) ModelManifest(ctx context.Context, clientVersions map[string]string,
	baseURL string) (map[string]any, error) {
	var out map[string]any
	err := c.post(ctx, "/internal/models/manifest", map[string]any{
		"client_versions": clientVersions, "base_url": baseURL,
	}, &out)
	return out, err
}

// ModelList returns all registered models.
func (c *Client) ModelList(ctx context.Context) (map[string]any, error) {
	var out map[string]any
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		c.baseURL+"/internal/models/list", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-Internal-Token", c.token)
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return nil, err
	}
	return out, nil
}

// ModelRuntime returns the ONNX runtime status (availability + loadable models).
func (c *Client) ModelRuntime(ctx context.Context) (map[string]any, error) {
	var out map[string]any
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		c.baseURL+"/internal/models/runtime", nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-Internal-Token", c.token)
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return nil, err
	}
	return out, nil
}

// Health pings the ML service.
func (c *Client) Health(ctx context.Context) error {
	req, _ := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"/health", nil)
	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("ml health: %d", resp.StatusCode)
	}
	return nil
}
