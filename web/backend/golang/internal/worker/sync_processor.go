package worker

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/peritanix/dape-backend/internal/handlers"
)

// sync_processor drains a server-side retry queue.
//
// When the Go API receives a delta-sync op that fails transiently (e.g. the DB
// is briefly unavailable), it can enqueue the raw op into a Redis list instead
// of dropping it. This worker periodically pops ops and re-dispatches them,
// with a bounded attempt count. Combined with the idempotency keys in
// /sync/delta, retries are safe (a replayed op returns "duplicate").
const (
	syncQueueKey     = "sync:retry_queue"
	syncMaxAttempt   = 5
	syncPollInterval = 30 * time.Second
)

type queuedOp struct {
	Entity         string          `json:"entity"`
	Op             string          `json:"op"`
	IdempotencyKey string          `json:"idempotency_key"`
	UpdatedTS      int64           `json:"updated_ts"`
	Data           json.RawMessage `json:"data"`
	Attempt        int             `json:"attempt"`
}

// StartSyncProcessor launches the background retry loop.
func StartSyncProcessor(deps *handlers.Deps) {
	go func() {
		ticker := time.NewTicker(syncPollInterval)
		defer ticker.Stop()
		slog.Info("sync-processor started", "poll", syncPollInterval)
		for range ticker.C {
			drainQueue(deps)
		}
	}()
}

// drainQueue pops and re-applies queued ops (bounded by queue depth per tick).
func drainQueue(deps *handlers.Deps) {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	const maxPerTick = 50
	for i := 0; i < maxPerTick; i++ {
		raw, err := deps.Cache.RDB.LPop(ctx, syncQueueKey).Result()
		if err == redis.Nil {
			return // queue empty
		}
		if err != nil {
			slog.Error("sync-processor pop", "err", err)
			return
		}
		var op queuedOp
		if err := json.Unmarshal([]byte(raw), &op); err != nil {
			slog.Warn("sync-processor bad payload", "err", err)
			continue
		}
		op.Attempt++
		// Idempotency: skip if already processed.
		dup, err := deps.Cache.SeenOrSet(ctx, op.IdempotencyKey, 24*time.Hour)
		if err != nil {
			requeue(deps, op)
			continue
		}
		if dup {
			continue // already applied elsewhere
		}
		if err := deps.ApplySyncOp(ctx, op.Entity, op.Data); err != nil {
			slog.Warn("sync-processor apply", "entity", op.Entity, "attempt", op.Attempt, "err", err)
			if op.Attempt < syncMaxAttempt {
				requeue(deps, op)
			} else {
				slog.Error("sync-processor dropping op", "key", op.IdempotencyKey, "attempts", op.Attempt)
			}
		}
	}
}

func requeue(deps *handlers.Deps, op queuedOp) {
	b, _ := json.Marshal(op)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := deps.Cache.RDB.RPush(ctx, syncQueueKey, b).Err(); err != nil {
		slog.Error("sync-processor requeue", "err", err)
	}
}

// EnqueueSyncOp is the producer used by the HTTP layer when a sync op fails
// transiently. Exported so handlers can push into the retry queue.
func EnqueueSyncOp(deps *handlers.Deps, entity, op, idemKey string, updatedTS int64, data json.RawMessage) error {
	item := queuedOp{Entity: entity, Op: op, IdempotencyKey: idemKey,
		UpdatedTS: updatedTS, Data: data, Attempt: 0}
	b, _ := json.Marshal(item)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	return deps.Cache.RDB.RPush(ctx, syncQueueKey, b).Err()
}
