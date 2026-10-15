// Package postgres implements the domain repository ports with pgx/PostGIS.
package postgres

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// Store wraps a pgx connection pool.
type Store struct {
	Pool *pgxpool.Pool
}

// New opens a pooled connection and waits until the DB is reachable.
func New(ctx context.Context, dsn string) (*Store, error) {
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("parse dsn: %w", err)
	}
	cfg.MaxConns = 10
	cfg.MaxConnLifetime = time.Hour

	var pool *pgxpool.Pool
	// Retry the initial connect so `make web-up` is resilient to DB warm-up.
	for i := 0; i < 30; i++ {
		pool, err = pgxpool.NewWithConfig(ctx, cfg)
		if err == nil {
			if perr := pool.Ping(ctx); perr == nil {
				return &Store{Pool: pool}, nil
			}
		}
		time.Sleep(2 * time.Second)
	}
	return nil, fmt.Errorf("connect postgres after retries: %w", err)
}

// Close releases the pool.
func (s *Store) Close() { s.Pool.Close() }
