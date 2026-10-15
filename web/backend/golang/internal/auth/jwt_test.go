package auth

import (
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func TestMintAndParse(t *testing.T) {
	iss := NewIssuer("test-secret-32-chars-minimum-abc", 1)
	tok, err := iss.Mint("user-1", "farmer", "devA")
	if err != nil {
		t.Fatalf("mint: %v", err)
	}
	claims, err := iss.Parse(tok)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if claims.UserID != "user-1" || claims.Role != "farmer" || claims.DeviceID != "devA" {
		t.Fatalf("claims mismatch: %+v", claims)
	}
}

func TestMintRequiresUserID(t *testing.T) {
	iss := NewIssuer("secret", 1)
	if _, err := iss.Mint("", "farmer", ""); err == nil {
		t.Fatal("expected error for empty user id")
	}
}

func TestParseRejectsWrongSecret(t *testing.T) {
	a := NewIssuer("secret-a", 1)
	b := NewIssuer("secret-b", 1)
	tok, _ := a.Mint("u", "farmer", "")
	if _, err := b.Parse(tok); err == nil {
		t.Fatal("token signed by a must not verify under b")
	}
}

func TestParseRejectsExpired(t *testing.T) {
	iss := &Issuer{secret: []byte("s"), ttl: -time.Hour} // already expired
	tok, err := iss.Mint("u", "farmer", "")
	if err != nil {
		t.Fatalf("mint: %v", err)
	}
	if _, err := iss.Parse(tok); err == nil {
		t.Fatal("expired token must fail")
	}
}

func TestParseRejectsNoneAlg(t *testing.T) {
	// Craft a token with alg=none; the parser must refuse it.
	claims := Claims{UserID: "attacker", Role: "farmer"}
	unsafe := jwt.NewWithClaims(jwt.SigningMethodNone, claims)
	tok, _ := unsafe.SignedString(jwt.UnsafeAllowNoneSignatureType)
	iss := NewIssuer("secret", 1)
	if _, err := iss.Parse(tok); err == nil {
		t.Fatal("alg=none token must be rejected")
	}
}

func TestTTLDefault(t *testing.T) {
	iss := NewIssuer("s", 0)
	if iss.TTL() != 720*time.Hour {
		t.Fatalf("default ttl = %v", iss.TTL())
	}
}

func TestPasswordHashRoundTrip(t *testing.T) {
	hash, err := HashPassword("correct horse battery")
	if err != nil {
		t.Fatalf("hash: %v", err)
	}
	if hash == "correct horse battery" {
		t.Fatal("hash must not equal plaintext")
	}
	if !CheckPassword(hash, "correct horse battery") {
		t.Fatal("correct password must verify")
	}
	if CheckPassword(hash, "wrong") {
		t.Fatal("wrong password must not verify")
	}
}
