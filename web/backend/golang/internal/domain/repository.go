package domain

import "context"

// LandRepo is the persistence port for lands.
type LandRepo interface {
	Create(ctx context.Context, l *Land) error
	GetByID(ctx context.Context, id string) (*Land, error)
	ListGeoJSON(ctx context.Context) ([]byte, error)
	UpdateGDD(ctx context.Context, id string, gdd float64, phase LandPhase) error
	ListAll(ctx context.Context) ([]Land, error)
}

// UserRepo is the persistence port for users.
type UserRepo interface {
	Create(ctx context.Context, u *User) error
	GetByID(ctx context.Context, id string) (*User, error)
	GetByPublicKey(ctx context.Context, pubkey string) (*User, error)
	// Register creates a user with a password hash in one call.
	Register(ctx context.Context, name, phone, role, passwordHash string) (*User, error)
	// GetCredential returns the password hash for a phone (login).
	GetCredential(ctx context.Context, phone string) (userID, passwordHash, role string, err error)
	// SetPublicKey stores the farmer's Ed25519 public key (and optional white
	// reference matrix JSON) so the buyer QR verifier can validate offline certs.
	SetPublicKey(ctx context.Context, userID, publicKey string, whiteReferenceJSON string) error
}

// SoilRepo is the persistence port for soil assessments.
type SoilRepo interface {
	Create(ctx context.Context, s *SoilAssessment) error
	ListByLand(ctx context.Context, landID string) ([]SoilAssessment, error)
	LatestByLand(ctx context.Context, landID string) (*SoilAssessment, error)
	// FertilityGeoJSON returns a FeatureCollection of soil points per land.
	FertilityGeoJSON(ctx context.Context) ([]byte, error)
}

// GDDPoint is one day of thermal accumulation for the dashboard chart.
type GDDPoint struct {
	Day    string  `json:"day"`
	TMin   float64 `json:"t_min"`
	TMax   float64 `json:"t_max"`
	GDDDay float64 `json:"gdd_day"`
	GDDCum float64 `json:"gdd_cum"`
}

// GDDRepo reads the GDD daily time series.
type GDDRepo interface {
	History(ctx context.Context, landID string, days int) ([]GDDPoint, error)
	// AppendDaily records one day of GDD for a land (idempotent per day).
	AppendDaily(ctx context.Context, landID, day string, tMin, tMax, gddDay, gddCum float64) error
}

// Feedback is a ground-truth correction for a model prediction.
type Feedback struct {
	ModelName   string         `json:"model_name"`
	Inputs      map[string]any `json:"inputs"`
	Predicted   map[string]any `json:"predicted"`
	Corrected   map[string]any `json:"corrected"`
	LandID      string         `json:"land_id,omitempty"`
	DiagnosisID string         `json:"diagnosis_id,omitempty"`
	Source      string         `json:"source"`
	Confidence  float64        `json:"confidence"`
}

// FeedbackRepo persists model feedback and reports retrain readiness.
type FeedbackRepo interface {
	Create(ctx context.Context, f *Feedback) (string, error)
	// Counts returns labelled-example counts per model.
	Counts(ctx context.Context) (map[string]int64, error)
}

// ClimateReading is one microclimate observation from a field sensor logger.
type ClimateReading struct {
	ObservedAt string  `json:"observed_at"` // RFC3339
	RH         float64 `json:"rh"`
	TempC      float64 `json:"temp_c"`
	SoilMoist  float64 `json:"soil_moist,omitempty"`
}

// ClimateRepo persists microclimate readings and returns recent windows.
type ClimateRepo interface {
	// Ingest upserts a batch of readings for a land (idempotent per timestamp).
	Ingest(ctx context.Context, landID, deviceID string, readings []ClimateReading) (int, error)
	// Series returns the last `hours` readings (chronological).
	Series(ctx context.Context, landID string, hours int) ([]ClimateReading, error)
}

// DiseaseRepo is the persistence port for disease logs.
type DiseaseRepo interface {
	Create(ctx context.Context, d *DiseaseLog) error
	ListByLand(ctx context.Context, landID string) ([]DiseaseLog, error)
	GeoJSON(ctx context.Context, landID string) ([]byte, error)
	NearbyCount(ctx context.Context, landID string, lon, lat float64, radiusM float64) (int, error)
	// SpatialContext returns nearby count, distance-to-edge (m) and bearing (deg).
	SpatialContext(ctx context.Context, landID string, lon, lat float64, radiusM float64) (SpatialContext, error)
	// NearbyPoints returns infection points within radius with their edge distance.
	NearbyPoints(ctx context.Context, landID string, lon, lat, radiusM float64) ([]NearbyPoint, error)
}

// NearbyPoint is a diagnosed point near a query location.
type NearbyPoint struct {
	Lon       float64 `json:"lon"`
	Lat       float64 `json:"lat"`
	Severity  float64 `json:"severity_pct"`
	DistEdgeM float64 `json:"distance_to_edge_m"`
	LocalTWI  float64 `json:"local_twi"`
}

// SpatialContext is the geospatial enrichment for a diagnosis point.
type SpatialContext struct {
	NearbyInfected int     `json:"nearby_infected"`
	DistEdgeM      float64 `json:"distance_to_edge_m"`
	BearingDeg     float64 `json:"bearing_deg"`
}

// HarvestRepo is the persistence port for harvest batches.
type HarvestRepo interface {
	Create(ctx context.Context, h *HarvestBatch) error
	GetByBatchCode(ctx context.Context, code string) (*HarvestBatch, error)
	ListByLand(ctx context.Context, landID string) ([]HarvestBatch, error)
}
