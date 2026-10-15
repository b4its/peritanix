package postgres

import (
	"context"
	"encoding/json"
	"time"

	"github.com/peritanix/dape-backend/internal/domain"
)

func nowUnix() int64 { return time.Now().Unix() }

// --- UserRepo ---------------------------------------------------------------

// UserRepo is the Postgres-backed user repository.
type UserRepo struct{ s *Store }

// NewUserRepo constructs a UserRepo.
func NewUserRepo(s *Store) *UserRepo { return &UserRepo{s: s} }

func (r *UserRepo) Create(ctx context.Context, u *domain.User) error {
	const q = `
		INSERT INTO users (id, name, role, phone, ed25519_public_key, white_reference)
		VALUES (COALESCE(NULLIF($1,'')::uuid, gen_random_uuid()), $2, $3, $4, $5, $6)
		RETURNING id, created_at`
	wr, _ := json.Marshal(u.WhiteReference)
	return r.s.Pool.QueryRow(ctx, q, u.ID, u.Name, u.Role, u.Phone,
		u.Ed25519PublicKey, wr).Scan(&u.ID, &u.CreatedAt)
}

func (r *UserRepo) GetByID(ctx context.Context, id string) (*domain.User, error) {
	const q = `SELECT id, name, role, COALESCE(phone,''), COALESCE(ed25519_public_key,''),
	                  white_reference, created_at FROM users WHERE id=$1`
	return r.scanUser(ctx, q, id)
}

func (r *UserRepo) GetByPublicKey(ctx context.Context, pubkey string) (*domain.User, error) {
	const q = `SELECT id, name, role, COALESCE(phone,''), COALESCE(ed25519_public_key,''),
	                  white_reference, created_at FROM users WHERE ed25519_public_key=$1 LIMIT 1`
	return r.scanUser(ctx, q, pubkey)
}

func (r *UserRepo) scanUser(ctx context.Context, q string, arg any) (*domain.User, error) {
	u := &domain.User{}
	var wr []byte
	err := r.s.Pool.QueryRow(ctx, q, arg).Scan(&u.ID, &u.Name, &u.Role, &u.Phone,
		&u.Ed25519PublicKey, &wr, &u.CreatedAt)
	if err != nil {
		return nil, err
	}
	if len(wr) > 0 {
		_ = json.Unmarshal(wr, &u.WhiteReference)
	}
	return u, nil
}

// --- SoilRepo ---------------------------------------------------------------

// SoilRepo is the Postgres-backed soil assessment repository.
type SoilRepo struct{ s *Store }

// NewSoilRepo constructs a SoilRepo.
func NewSoilRepo(s *Store) *SoilRepo { return &SoilRepo{s: s} }

func (r *SoilRepo) Create(ctx context.Context, a *domain.SoilAssessment) error {
	const q = `
		INSERT INTO soil_assessments (land_id, sand_pct, silt_pct, clay_pct, soil_class,
		       cec_meq, ph_deterministic, nitrate_ppm, dark_chamber_lux_check, rev, updated_ts)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,1,$10)
		RETURNING id`
	return r.s.Pool.QueryRow(ctx, q, a.LandID, a.SandPct, a.SiltPct, a.ClayPct,
		a.SoilClass, a.CECMeq, a.PHRDeterministic, a.NitratePPM,
		a.DarkChamberLuxCheck, a.UpdatedTS).Scan(&a.ID)
}

func (r *SoilRepo) ListByLand(ctx context.Context, landID string) ([]domain.SoilAssessment, error) {
	const q = `
		SELECT id, land_id, COALESCE(sand_pct,0), COALESCE(silt_pct,0), COALESCE(clay_pct,0),
		       COALESCE(soil_class,''), COALESCE(cec_meq,0), COALESCE(ph_deterministic,0),
		       COALESCE(nitrate_ppm,0), dark_chamber_lux_check, updated_ts, rev
		FROM soil_assessments WHERE land_id=$1 ORDER BY created_at DESC`
	rows, err := r.s.Pool.Query(ctx, q, landID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []domain.SoilAssessment
	for rows.Next() {
		var a domain.SoilAssessment
		if err := rows.Scan(&a.ID, &a.LandID, &a.SandPct, &a.SiltPct, &a.ClayPct,
			&a.SoilClass, &a.CECMeq, &a.PHRDeterministic, &a.NitratePPM,
			&a.DarkChamberLuxCheck, &a.UpdatedTS, &a.Rev); err != nil {
			return nil, err
		}
		out = append(out, a)
	}
	return out, rows.Err()
}

func (r *SoilRepo) LatestByLand(ctx context.Context, landID string) (*domain.SoilAssessment, error) {
	list, err := r.ListByLand(ctx, landID)
	if err != nil || len(list) == 0 {
		return nil, err
	}
	return &list[0], nil
}

// --- DiseaseRepo ------------------------------------------------------------

// DiseaseRepo is the PostGIS-backed disease log repository.
type DiseaseRepo struct{ s *Store }

// NewDiseaseRepo constructs a DiseaseRepo.
func NewDiseaseRepo(s *Store) *DiseaseRepo { return &DiseaseRepo{s: s} }

func (r *DiseaseRepo) Create(ctx context.Context, d *domain.DiseaseLog) error {
	const q = `
		INSERT INTO disease_logs (land_id, point_location, local_twi,
		       posterior_probabilities, confirmed_disease, severity_pct,
		       confidence_level, protocol_code, rev, updated_ts)
		VALUES ($1, ST_SetSRID(ST_MakePoint($2,$3),4326), $4, $5, $6, $7, $8, $9, 1, $10)
		RETURNING id`
	post, _ := json.Marshal(d.PosteriorProbabilities)
	return r.s.Pool.QueryRow(ctx, q, d.LandID, d.Lon, d.Lat, d.LocalTWI, post,
		d.ConfirmedDisease, d.SeverityPct, d.ConfidenceLevel, d.ProtocolCode,
		d.UpdatedTS).Scan(&d.ID)
}

func (r *DiseaseRepo) ListByLand(ctx context.Context, landID string) ([]domain.DiseaseLog, error) {
	const q = `
		SELECT id, land_id, ST_X(point_location), ST_Y(point_location), COALESCE(local_twi,0),
		       COALESCE(posterior_probabilities,'{}'), COALESCE(confirmed_disease,''),
		       COALESCE(severity_pct,0), COALESCE(confidence_level,''), COALESCE(protocol_code,''),
		       updated_ts, rev
		FROM disease_logs WHERE land_id=$1 AND point_location IS NOT NULL
		ORDER BY created_at DESC`
	rows, err := r.s.Pool.Query(ctx, q, landID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []domain.DiseaseLog
	for rows.Next() {
		var d domain.DiseaseLog
		var post []byte
		if err := rows.Scan(&d.ID, &d.LandID, &d.Lon, &d.Lat, &d.LocalTWI, &post,
			&d.ConfirmedDisease, &d.SeverityPct, &d.ConfidenceLevel, &d.ProtocolCode,
			&d.UpdatedTS, &d.Rev); err != nil {
			return nil, err
		}
		_ = json.Unmarshal(post, &d.PosteriorProbabilities)
		out = append(out, d)
	}
	return out, rows.Err()
}

func (r *DiseaseRepo) GeoJSON(ctx context.Context, landID string) ([]byte, error) {
	var js []byte
	if err := r.s.Pool.QueryRow(ctx, `SELECT disease_geojson($1)`, landID).Scan(&js); err != nil {
		return nil, err
	}
	return js, nil
}

func (r *DiseaseRepo) NearbyCount(ctx context.Context, landID string, lon, lat, radiusM float64) (int, error) {
	const q = `SELECT nearby_infected_count($1, ST_SetSRID(ST_MakePoint($2,$3),4326), $4)`
	var n int
	err := r.s.Pool.QueryRow(ctx, q, landID, lon, lat, radiusM).Scan(&n)
	return n, err
}

// --- HarvestRepo ------------------------------------------------------------

// HarvestRepo is the Postgres-backed harvest batch repository.
type HarvestRepo struct{ s *Store }

// NewHarvestRepo constructs a HarvestRepo.
func NewHarvestRepo(s *Store) *HarvestRepo { return &HarvestRepo{s: s} }

func (r *HarvestRepo) Create(ctx context.Context, h *domain.HarvestBatch) error {
	const q = `
		INSERT INTO harvest_batches (land_id, batch_code, boiling_point_calc, hot_water_ratio,
		       archimedes_sink_ratio, grade_result, payload_hash, crypto_signature,
		       signer_public_key, rev, updated_ts)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,1,$10)
		RETURNING id`
	return r.s.Pool.QueryRow(ctx, q, h.LandID, h.BatchCode, h.BoilingPointCalc,
		h.HotWaterRatio, h.ArchimedesSinkRatio, h.GradeResult, h.PayloadHash,
		h.CryptoSignature, h.SignerPublicKey, h.UpdatedTS).Scan(&h.ID)
}

func (r *HarvestRepo) GetByBatchCode(ctx context.Context, code string) (*domain.HarvestBatch, error) {
	const q = `
		SELECT id, land_id, batch_code, COALESCE(boiling_point_calc,0), COALESCE(hot_water_ratio,''),
		       COALESCE(archimedes_sink_ratio,0), COALESCE(grade_result,''), COALESCE(payload_hash,''),
		       COALESCE(crypto_signature,''), COALESCE(signer_public_key,''), updated_ts, rev
		FROM harvest_batches WHERE batch_code=$1`
	h := &domain.HarvestBatch{}
	err := r.s.Pool.QueryRow(ctx, q, code).Scan(&h.ID, &h.LandID, &h.BatchCode,
		&h.BoilingPointCalc, &h.HotWaterRatio, &h.ArchimedesSinkRatio, &h.GradeResult,
		&h.PayloadHash, &h.CryptoSignature, &h.SignerPublicKey, &h.UpdatedTS, &h.Rev)
	if err != nil {
		return nil, err
	}
	return h, nil
}

func (r *HarvestRepo) ListByLand(ctx context.Context, landID string) ([]domain.HarvestBatch, error) {
	const q = `
		SELECT id, land_id, batch_code, COALESCE(boiling_point_calc,0), COALESCE(hot_water_ratio,''),
		       COALESCE(archimedes_sink_ratio,0), COALESCE(grade_result,''), COALESCE(payload_hash,''),
		       COALESCE(crypto_signature,''), COALESCE(signer_public_key,''), updated_ts, rev
		FROM harvest_batches WHERE land_id=$1 ORDER BY created_at DESC`
	rows, err := r.s.Pool.Query(ctx, q, landID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []domain.HarvestBatch
	for rows.Next() {
		var h domain.HarvestBatch
		if err := rows.Scan(&h.ID, &h.LandID, &h.BatchCode, &h.BoilingPointCalc,
			&h.HotWaterRatio, &h.ArchimedesSinkRatio, &h.GradeResult, &h.PayloadHash,
			&h.CryptoSignature, &h.SignerPublicKey, &h.UpdatedTS, &h.Rev); err != nil {
			return nil, err
		}
		out = append(out, h)
	}
	return out, rows.Err()
}
