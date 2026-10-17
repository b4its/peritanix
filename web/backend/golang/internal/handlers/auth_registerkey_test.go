package handlers

import "testing"

func TestValidEd25519PublicKey(t *testing.T) {
	cases := []struct {
		name string
		key  string
		want bool
	}{
		{"base64 44", "5w3n2XrVd8g5b1j3q4h6c9s2u7y0a4b8c1d2e3f4g5h6k=", true},   // 44 chars
		{"hex 64", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", true},
		{"empty", "", false},
		{"too short", "abc123", false},
		{"too long", "x0123456789012345678901234567890123456789012345678901234567890123", false},
	}
	for _, c := range cases {
		// Normalize the two "true" fixtures to exact lengths regardless of hand count.
		k := c.key
		if c.name == "base64 44" && len(k) != 44 {
			for len(k) < 44 {
				k += "A"
			}
			k = k[:44]
		}
		if c.name == "hex 64" && len(k) != 64 {
			for len(k) < 64 {
				k += "0"
			}
			k = k[:64]
		}
		if got := validEd25519PublicKey(k); got != c.want {
			t.Errorf("%s: validEd25519PublicKey(len=%d) = %v, want %v", c.name, len(k), got, c.want)
		}
	}
}
