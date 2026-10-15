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
}

// SoilRepo is the persistence port for soil assessments.
type SoilRepo interface {
	Create(ctx context.Context, s *SoilAssessment) error
	ListByLand(ctx context.Context, landID string) ([]SoilAssessment, error)
	LatestByLand(ctx context.Context, landID string) (*SoilAssessment, error)
}

// DiseaseRepo is the persistence port for disease logs.
type DiseaseRepo interface {
	Create(ctx context.Context, d *DiseaseLog) error
	ListByLand(ctx context.Context, landID string) ([]DiseaseLog, error)
	GeoJSON(ctx context.Context, landID string) ([]byte, error)
	NearbyCount(ctx context.Context, landID string, lon, lat float64, radiusM float64) (int, error)
}

// HarvestRepo is the persistence port for harvest batches.
type HarvestRepo interface {
	Create(ctx context.Context, h *HarvestBatch) error
	GetByBatchCode(ctx context.Context, code string) (*HarvestBatch, error)
	ListByLand(ctx context.Context, landID string) ([]HarvestBatch, error)
}
