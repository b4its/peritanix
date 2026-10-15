package postgres

import (
	"context"

	"github.com/peritanix/dape-backend/internal/domain"
)

// ClimateRepo persists microclimate sensor readings.
type ClimateRepo struct{ s *Store }

// NewClimateRepo constructs a ClimateRepo.
func NewClimateRepo(s *Store) *ClimateRepo { return &ClimateRepo{s: s} }

// Ingest upserts a batch of readings (idempotent on land_id+observed_at).
func (r *ClimateRepo) Ingest(ctx context.Context, landID, deviceID string,
	readings []domain.ClimateReading) (int, error) {
	const q = `
		INSERT INTO climate_readings (land_id, observed_at, rh, temp_c, soil_moist, device_id)
		VALUES ($1, $2::timestamptz, $3, $4, NULLIF($5,0), $6)
		ON CONFLICT (land_id, observed_at) DO UPDATE SET
		  rh = EXCLUDED.rh, temp_c = EXCLUDED.temp_c,
		  soil_moist = EXCLUDED.soil_moist, device_id = EXCLUDED.device_id`
	n := 0
	for _, rd := range readings {
		if _, err := r.s.Pool.Exec(ctx, q, landID, rd.ObservedAt, rd.RH,
			rd.TempC, rd.SoilMoist, deviceID); err != nil {
			return n, err
		}
		n++
	}
	return n, nil
}

// Series returns the last `hours` readings in chronological order.
func (r *ClimateRepo) Series(ctx context.Context, landID string, hours int) ([]domain.ClimateReading, error) {
	if hours <= 0 {
		hours = 72
	}
	const q = `
		SELECT to_char(observed_at, 'YYYY-MM-DD"T"HH24:MI:SSOF'), rh, temp_c,
		       COALESCE(soil_moist, 0)
		FROM climate_readings
		WHERE land_id = $1 AND observed_at >= now() - make_interval(hours => $2)
		ORDER BY observed_at ASC`
	rows, err := r.s.Pool.Query(ctx, q, landID, hours)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []domain.ClimateReading
	for rows.Next() {
		var rd domain.ClimateReading
		if err := rows.Scan(&rd.ObservedAt, &rd.RH, &rd.TempC, &rd.SoilMoist); err != nil {
			return nil, err
		}
		out = append(out, rd)
	}
	return out, rows.Err()
}
