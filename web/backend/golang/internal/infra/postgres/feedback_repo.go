package postgres

import (
	"context"
	"encoding/json"

	"github.com/peritanix/dape-backend/internal/domain"
)

// FeedbackRepo persists continuous-learning feedback.
type FeedbackRepo struct{ s *Store }

// NewFeedbackRepo constructs a FeedbackRepo.
func NewFeedbackRepo(s *Store) *FeedbackRepo { return &FeedbackRepo{s: s} }

// Create stores a feedback row and returns its id.
func (r *FeedbackRepo) Create(ctx context.Context, f *domain.Feedback) (string, error) {
	const q = `
		INSERT INTO ml_feedback (model_name, inputs, predicted, corrected,
		       land_id, diagnosis_id, source, confidence)
		VALUES ($1, $2, $3, $4, NULLIF($5,'')::uuid, NULLIF($6,'')::uuid, $7, $8)
		RETURNING id`
	in, _ := json.Marshal(f.Inputs)
	pred, _ := json.Marshal(f.Predicted)
	corr, _ := json.Marshal(f.Corrected)
	var id string
	err := r.s.Pool.QueryRow(ctx, q, f.ModelName, in, pred, corr,
		f.LandID, f.DiagnosisID, f.Source, f.Confidence).Scan(&id)
	return id, err
}

// Counts returns labelled-example counts per model.
func (r *FeedbackRepo) Counts(ctx context.Context) (map[string]int64, error) {
	rows, err := r.s.Pool.Query(ctx, `SELECT model_name, n FROM feedback_counts()`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]int64{}
	for rows.Next() {
		var name string
		var n int64
		if err := rows.Scan(&name, &n); err != nil {
			return nil, err
		}
		out[name] = n
	}
	return out, rows.Err()
}
