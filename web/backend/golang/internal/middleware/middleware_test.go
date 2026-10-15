package middleware

import (
	"testing"
	"time"
)

func TestIsUUID(t *testing.T) {
	valid := []string{
		"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
		"11111111-1111-1111-1111-111111111111",
		"01234567-89AB-CDEF-0123-456789abcdef",
	}
	for _, v := range valid {
		if !IsUUID(v) {
			t.Errorf("expected %q valid", v)
		}
	}
	invalid := []string{
		"", "not-a-uuid", "123", "aaaa-aaaa-aaaa-aaaa-aaaa",
		"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa-extra",
		"' OR 1=1 --", "gggggggg-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
	}
	for _, v := range invalid {
		if IsUUID(v) {
			t.Errorf("expected %q invalid", v)
		}
	}
}

func TestRateLimiterAllowsThenBlocks(t *testing.T) {
	rl := NewRateLimiter(3, time.Minute)
	for i := 0; i < 3; i++ {
		if !rl.allow("1.2.3.4") {
			t.Fatalf("request %d should be allowed", i+1)
		}
	}
	if rl.allow("1.2.3.4") {
		t.Fatal("4th request should be blocked")
	}
	// A different key is unaffected.
	if !rl.allow("5.6.7.8") {
		t.Fatal("different client should be allowed")
	}
}

func TestRateLimiterWindowResets(t *testing.T) {
	rl := NewRateLimiter(1, 50*time.Millisecond)
	if !rl.allow("k") {
		t.Fatal("first allowed")
	}
	if rl.allow("k") {
		t.Fatal("second blocked")
	}
	time.Sleep(60 * time.Millisecond)
	if !rl.allow("k") {
		t.Fatal("should be allowed after window resets")
	}
}

func TestRateLimiterSweep(t *testing.T) {
	rl := NewRateLimiter(1, 20*time.Millisecond)
	rl.allow("a")
	rl.allow("b")
	time.Sleep(30 * time.Millisecond)
	rl.sweep()
	rl.mu.Lock()
	n := len(rl.hits)
	rl.mu.Unlock()
	if n != 0 {
		t.Fatalf("expected sweep to clear expired windows, got %d", n)
	}
}

func TestNewIDLength(t *testing.T) {
	id := newID()
	if len(id) != 16 {
		t.Fatalf("newID length = %d, want 16", len(id))
	}
	if id == newID() {
		t.Fatal("two ids should differ")
	}
}
