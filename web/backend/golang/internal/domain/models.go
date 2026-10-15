// Package domain contains DAPE core entities and repository ports.
package domain

import "time"

// LandPhase mirrors the Postgres enum.
type LandPhase string

const (
	PhaseVegetative          LandPhase = "VEGETATIVE"
	PhaseFlowering           LandPhase = "FLOWERING"
	PhaseFruiting            LandPhase = "FRUITING"
	PhaseHarvest             LandPhase = "HARVEST"
	PhaseFortificationLocked LandPhase = "FORTIFICATION_LOCKED"
)

// User is an actor in the system (farmer, worker, agronomist, ...).
type User struct {
	ID               string    `json:"id"`
	Name             string    `json:"name"`
	Role             string    `json:"role"`
	Phone            string    `json:"phone,omitempty"`
	Ed25519PublicKey string    `json:"ed25519_public_key,omitempty"`
	WhiteReference   []float64 `json:"white_reference,omitempty"`
	CreatedAt        time.Time `json:"created_at"`
}

// Land is a planted plot with a geospatial boundary.
type Land struct {
	ID              string    `json:"id"`
	UserID          string    `json:"user_id"`
	Name            string    `json:"name"`
	BoundaryWKT     string    `json:"boundary_wkt"`
	AreaHa          float64   `json:"area_ha"`
	ElevationM      float64   `json:"elevation_m"`
	PlantingDate    string    `json:"planting_date,omitempty"`
	AccumulatedGDD  float64   `json:"accumulated_gdd"`
	CurrentPhase    LandPhase `json:"current_phase"`
	TWIRasterURL    string    `json:"twi_raster_url,omitempty"`
	UpdatedTS       int64     `json:"updated_ts"`
	Rev             int64     `json:"rev"`
}

// SoilAssessment captures a Stokes + dark-chamber soil reading.
type SoilAssessment struct {
	ID                  string  `json:"id"`
	LandID              string  `json:"land_id"`
	SandPct             float64 `json:"sand_pct"`
	SiltPct             float64 `json:"silt_pct"`
	ClayPct             float64 `json:"clay_pct"`
	SoilClass           string  `json:"soil_class"`
	CECMeq              float64 `json:"cec_meq"`
	PHRDeterministic    float64 `json:"ph_deterministic"`
	NitratePPM          float64 `json:"nitrate_ppm"`
	DarkChamberLuxCheck bool    `json:"dark_chamber_lux_check"`
	UpdatedTS           int64   `json:"updated_ts"`
	Rev                 int64   `json:"rev"`
}

// DiseaseLog is a confirmed multi-modal diagnosis record.
type DiseaseLog struct {
	ID                   string             `json:"id"`
	LandID               string             `json:"land_id"`
	Lon                  float64            `json:"lon"`
	Lat                  float64            `json:"lat"`
	LocalTWI             float64            `json:"local_twi"`
	PosteriorProbabilities map[string]float64 `json:"posterior_probabilities"`
	ConfirmedDisease     string             `json:"confirmed_disease"`
	SeverityPct          float64            `json:"severity_pct"`
	ConfidenceLevel      string             `json:"confidence_level"`
	ProtocolCode         string             `json:"protocol_code"`
	UpdatedTS            int64              `json:"updated_ts"`
	Rev                  int64              `json:"rev"`
}

// HarvestBatch is a signed post-harvest quality certificate.
type HarvestBatch struct {
	ID                 string  `json:"id"`
	LandID             string  `json:"land_id"`
	BatchCode          string  `json:"batch_code"`
	BoilingPointCalc   float64 `json:"boiling_point_calc"`
	HotWaterRatio      string  `json:"hot_water_ratio"`
	ArchimedesSinkRatio float64 `json:"archimedes_sink_ratio"`
	GradeResult        string  `json:"grade_result"`
	PayloadHash        string  `json:"payload_hash"`
	CryptoSignature    string  `json:"crypto_signature"`
	SignerPublicKey    string  `json:"signer_public_key"`
	UpdatedTS          int64   `json:"updated_ts"`
	Rev                int64   `json:"rev"`
}
