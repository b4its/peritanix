// Package auth issues and verifies JWT access tokens for the DAPE API.
//
// Password hashing uses bcrypt (golang.org/x/crypto). Tokens are HS256 JWTs
// carrying the user id, role and a short device id. The middleware is applied
// to all mutating routes; a small allow-list stays public (health, land
// geojson read, certificates/verify, and auth endpoints themselves).
package auth

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// Claims is the DAPE JWT payload.
type Claims struct {
	UserID   string `json:"uid"`
	Role     string `json:"role"`
	DeviceID string `json:"dev,omitempty"`
	jwt.RegisteredClaims
}

// Issuer mints and validates tokens.
type Issuer struct {
	secret []byte
	ttl    time.Duration
}

// NewIssuer constructs an Issuer. ttlHours <= 0 defaults to 720h (30 days).
func NewIssuer(secret string, ttlHours int) *Issuer {
	if ttlHours <= 0 {
		ttlHours = 720
	}
	return &Issuer{secret: []byte(secret), ttl: time.Duration(ttlHours) * time.Hour}
}

// Mint creates a signed token for a user.
func (i *Issuer) Mint(userID, role, deviceID string) (string, error) {
	if userID == "" {
		return "", errors.New("user id required")
	}
	now := time.Now()
	claims := Claims{
		UserID:   userID,
		Role:     role,
		DeviceID: deviceID,
		RegisteredClaims: jwt.RegisteredClaims{
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(i.ttl)),
			Issuer:    "dape",
		},
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(i.secret)
}

// Parse validates a token string and returns its claims.
func (i *Issuer) Parse(tokenStr string) (*Claims, error) {
	claims := &Claims{}
	tok, err := jwt.ParseWithClaims(tokenStr, claims, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return i.secret, nil
	})
	if err != nil {
		return nil, err
	}
	if !tok.Valid {
		return nil, errors.New("invalid token")
	}
	return claims, nil
}

// TTL returns the configured token lifetime.
func (i *Issuer) TTL() time.Duration { return i.ttl }
