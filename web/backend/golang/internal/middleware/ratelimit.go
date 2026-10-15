package middleware

import (
	"sync"
	"time"

	"github.com/gofiber/fiber/v2"
)

// RateLimiter is a fixed-window counter keyed by client IP (or a custom key).
// In-memory and dependency-free; for multi-replica deployments swap the store
// for Redis (the interface is intentionally tiny).
type RateLimiter struct {
	mu        sync.Mutex
	hits      map[string]*window
	limit     int
	perWindow time.Duration
}

type window struct {
	count int
	start time.Time
}

// NewRateLimiter constructs a limiter allowing `limit` requests per `per`.
func NewRateLimiter(limit int, per time.Duration) *RateLimiter {
	return &RateLimiter{hits: map[string]*window{}, limit: limit, perWindow: per}
}

// allow reports whether the key is under the limit for the current window.
func (r *RateLimiter) allow(key string) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	now := time.Now()
	w, ok := r.hits[key]
	if !ok || now.Sub(w.start) >= r.perWindow {
		r.hits[key] = &window{count: 1, start: now}
		return true
	}
	if w.count >= r.limit {
		return false
	}
	w.count++
	return true
}

// Middleware returns a Fiber handler that enforces the limit per client IP.
func (r *RateLimiter) Middleware() fiber.Handler {
	return func(c *fiber.Ctx) error {
		key := c.IP()
		if !r.allow(key) {
			c.Set("Retry-After", r.perWindow.String())
			return fiber.NewError(fiber.StatusTooManyRequests, "rate limit exceeded")
		}
		return c.Next()
	}
}

// sweep drops expired windows (call periodically to bound memory).
func (r *RateLimiter) sweep() {
	r.mu.Lock()
	defer r.mu.Unlock()
	now := time.Now()
	for k, w := range r.hits {
		if now.Sub(w.start) >= r.perWindow {
			delete(r.hits, k)
		}
	}
}

// StartSweeper runs periodic cleanup until stop is closed.
func (r *RateLimiter) StartSweeper(interval time.Duration, stop <-chan struct{}) {
	go func() {
		t := time.NewTicker(interval)
		defer t.Stop()
		for {
			select {
			case <-t.C:
				r.sweep()
			case <-stop:
				return
			}
		}
	}()
}
