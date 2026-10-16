package mlclient

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

// newTestServer returns a server that fails 500 for the first `failTimes`
// requests, then succeeds — used to exercise the circuit breaker.
func newTestServer(t *testing.T, failTimes int32, okBody any) *httptest.Server {
	t.Helper()
	var calls int32
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		n := atomic.AddInt32(&calls, 1)
		if n <= failTimes {
			w.WriteHeader(http.StatusInternalServerError)
			_, _ = w.Write([]byte(`{"detail":"boom"}`))
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(okBody)
	}))
}

func TestPostSuccess(t *testing.T) {
	body := map[string]any{"final_verdict": "Antraknosa"}
	srv := newTestServer(t, 0, body)
	defer srv.Close()

	c := New(srv.URL, "tok")
	out, err := c.BayesianDiagnosis(context.Background(), map[string]any{"land_id": "x"})
	if err != nil {
		t.Fatalf("expected success, got %v", err)
	}
	if out["final_verdict"] != "Antraknosa" {
		t.Fatalf("unexpected body: %v", out)
	}
}

func TestInternalTokenHeaderSent(t *testing.T) {
	var got string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got = r.Header.Get("X-Internal-Token")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()

	c := New(srv.URL, "secret-token")
	_, _ = c.BayesianDiagnosis(context.Background(), map[string]any{})
	if got != "secret-token" {
		t.Fatalf("token header = %q, want secret-token", got)
	}
}

func TestServerErrorPropagates(t *testing.T) {
	srv := newTestServer(t, 1, map[string]any{})
	defer srv.Close()

	c := New(srv.URL, "tok")
	if _, err := c.BayesianDiagnosis(context.Background(), map[string]any{}); err == nil {
		t.Fatal("expected error on 500")
	}
}

func TestCircuitBreakerOpensAfterThreshold(t *testing.T) {
	// Always fails.
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	c := New(srv.URL, "tok")
	// Trip the breaker: failThreshold (5) consecutive failures.
	for i := 0; i < failThreshold; i++ {
		_, _ = c.BayesianDiagnosis(context.Background(), map[string]any{})
	}
	if !c.breakerOpen() {
		t.Fatal("breaker should be open after threshold failures")
	}
	// Subsequent calls short-circuit without hitting the server.
	start := time.Now()
	_, err := c.BayesianDiagnosis(context.Background(), map[string]any{})
	if err == nil || err.Error() != "ml client circuit open" {
		t.Fatalf("expected circuit-open error, got %v", err)
	}
	if time.Since(start) > 100*time.Millisecond {
		t.Fatal("short-circuit should be immediate")
	}
}

func TestCircuitBreakerRecoversAfterCooldown(t *testing.T) {
	// Fail exactly the first `failThreshold` calls, then succeed.
	var calls int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if atomic.AddInt32(&calls, 1) <= failThreshold {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"ok":true}`))
	}))
	defer srv.Close()

	c := New(srv.URL, "tok")
	// Trip the breaker.
	for i := 0; i < failThreshold; i++ {
		_, _ = c.BayesianDiagnosis(context.Background(), map[string]any{})
	}
	// Force the cooldown to elapse deterministically.
	c.mu.Lock()
	c.openUntil = time.Now().Add(-time.Second)
	c.mu.Unlock()

	// A success must reset the breaker.
	if _, err := c.BayesianDiagnosis(context.Background(), map[string]any{}); err != nil {
		t.Fatalf("expected recovery success, got %v", err)
	}
	if c.breakerOpen() {
		t.Fatal("breaker should be closed after a successful call")
	}
}

func TestHealth(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/health" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"status":"ok"}`))
	}))
	defer srv.Close()

	c := New(srv.URL, "tok")
	if err := c.Health(context.Background()); err != nil {
		t.Fatalf("health failed: %v", err)
	}
}

func TestHealthFailsOnBadStatus(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer srv.Close()

	c := New(srv.URL, "tok")
	if err := c.Health(context.Background()); err == nil {
		t.Fatal("expected health error on 503")
	}
}

func TestConnectionRefused(t *testing.T) {
	// Point at a closed port.
	c := New("http://127.0.0.1:1", "tok")
	if _, err := c.BayesianDiagnosis(context.Background(), map[string]any{}); err == nil {
		t.Fatal("expected connection error")
	}
}
