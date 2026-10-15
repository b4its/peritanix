package postgres

import (
	"context"

	"github.com/peritanix/dape-backend/internal/domain"
)

// LandRepo is the PostGIS-backed land repository.
type LandRepo struct{ s *Store }

// NewLandRepo constructs a LandRepo.
func NewLandRepo(s *Store) *LandRepo { return &LandRepo{s: s} }

func (r *LandRepo) Create(ctx context.Context, l *domain.Land) error {
	const q = `
		INSERT INTO lands (user_id, name, boundary, elevation_m, planting_date,
		                   accumulated_gdd, current_phase, rev, updated_ts)
		VALUES ($1, $2, ST_GeomFromText($3, 4326), $4,
		        NULLIF($5,'')::date, $6, $7, 1, $8)
		RETURNING id, area_ha`
	return r.s.Pool.QueryRow(ctx, q,
		l.UserID, l.Name, l.BoundaryWKT, l.ElevationM, l.PlantingDate,
		l.AccumulatedGDD, string(l.CurrentPhase), l.UpdatedTS,
	).Scan(&l.ID, &l.AreaHa)
}

func (r *LandRepo) GetByID(ctx context.Context, id string) (*domain.Land, error) {
	const q = `
		SELECT id, user_id, name, ST_AsText(boundary), area_ha, COALESCE(elevation_m,0),
		       COALESCE(to_char(planting_date,'YYYY-MM-DD'),''), accumulated_gdd,
		       current_phase, COALESCE(twi_raster_url,''), updated_ts, rev
		FROM lands WHERE id = $1`
	l := &domain.Land{}
	err := r.s.Pool.QueryRow(ctx, q, id).Scan(
		&l.ID, &l.UserID, &l.Name, &l.BoundaryWKT, &l.AreaHa, &l.ElevationM,
		&l.PlantingDate, &l.AccumulatedGDD, &l.CurrentPhase, &l.TWIRasterURL,
		&l.UpdatedTS, &l.Rev,
	)
	if err != nil {
		return nil, err
	}
	return l, nil
}

func (r *LandRepo) ListAll(ctx context.Context) ([]domain.Land, error) {
	const q = `
		SELECT id, user_id, name, ST_AsText(boundary), area_ha, COALESCE(elevation_m,0),
		       COALESCE(to_char(planting_date,'YYYY-MM-DD'),''), accumulated_gdd,
		       current_phase, COALESCE(twi_raster_url,''), updated_ts, rev
		FROM lands ORDER BY created_at DESC`
	rows, err := r.s.Pool.Query(ctx, q)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []domain.Land
	for rows.Next() {
		var l domain.Land
		if err := rows.Scan(&l.ID, &l.UserID, &l.Name, &l.BoundaryWKT, &l.AreaHa,
			&l.ElevationM, &l.PlantingDate, &l.AccumulatedGDD, &l.CurrentPhase,
			&l.TWIRasterURL, &l.UpdatedTS, &l.Rev); err != nil {
			return nil, err
		}
		out = append(out, l)
	}
	return out, rows.Err()
}

func (r *LandRepo) ListGeoJSON(ctx context.Context) ([]byte, error) {
	var js []byte
	if err := r.s.Pool.QueryRow(ctx, `SELECT land_geojson()`).Scan(&js); err != nil {
		return nil, err
	}
	return js, nil
}

func (r *LandRepo) UpdateGDD(ctx context.Context, id string, gdd float64, phase domain.LandPhase) error {
	const q = `UPDATE lands SET accumulated_gdd=$2, current_phase=$3,
	           rev = rev + 1, updated_ts = $4 WHERE id=$1`
	_, err := r.s.Pool.Exec(ctx, q, id, gdd, string(phase), nowUnix())
	return err
}
