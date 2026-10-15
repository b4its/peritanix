// Package crypto verifies offline-signed DAPE quality certificates.
//
// Certificates are signed on-device with Ed25519 (Android Keystore / BouncyCastle)
// over a canonical JSON payload, and rendered as a QR code. A buyer scans the QR
// and the CBOR/JSON {payload, signature, public_key} triple is verified here —
// working even if the farmer never synced to the internet, because the public
// key travels inside the scanned payload.
package crypto

import (
	"crypto/ed25519"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"sort"
	"strconv"
	"strings"
)

// Certificate is the signed envelope embedded in the QR code.
type Certificate struct {
	Payload   map[string]any `json:"payload"`
	Signature string         `json:"signature"`  // base64 or hex
	PublicKey string         `json:"public_key"` // base64 or hex (ed25519, 32 bytes)
}

// VerifyResult is the outcome of a verification.
type VerifyResult struct {
	Valid     bool           `json:"valid"`
	Reason    string         `json:"reason,omitempty"`
	Payload   map[string]any `json:"payload,omitempty"`
	PublicKey string         `json:"public_key,omitempty"`
}

// Verify checks the Ed25519 signature over the *canonical* JSON encoding of the
// payload. Canonicalisation = JSON with sorted keys, no insignificant spaces.
func Verify(cert Certificate) VerifyResult {
	pub, err := decodeKey(cert.PublicKey)
	if err != nil {
		return VerifyResult{Valid: false, Reason: "invalid public key: " + err.Error()}
	}
	if len(pub) != ed25519.PublicKeySize {
		return VerifyResult{Valid: false,
			Reason: fmt.Sprintf("public key must be %d bytes, got %d", ed25519.PublicKeySize, len(pub))}
	}
	sig, err := decodeKey(cert.Signature)
	if err != nil {
		return VerifyResult{Valid: false, Reason: "invalid signature encoding: " + err.Error()}
	}
	if len(sig) != ed25519.SignatureSize {
		return VerifyResult{Valid: false,
			Reason: fmt.Sprintf("signature must be %d bytes, got %d", ed25519.SignatureSize, len(sig))}
	}
	msg, err := CanonicalJSON(cert.Payload)
	if err != nil {
		return VerifyResult{Valid: false, Reason: "canonicalisation failed: " + err.Error()}
	}
	if ed25519.Verify(ed25519.PublicKey(pub), msg, sig) {
		return VerifyResult{Valid: true, Payload: cert.Payload,
			PublicKey: cert.PublicKey}
	}
	return VerifyResult{Valid: false, Reason: "signature does not match payload"}
}

// decodeKey accepts base64 (std or url, padded or not) or hex.
//
// Hex is detected FIRST by charset because a 64-char hex string (32-byte key)
// is also syntactically valid base64 and would be mis-decoded otherwise.
func decodeKey(s string) ([]byte, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, errors.New("empty")
	}
	if isHex(s) {
		return hex.DecodeString(s)
	}
	if b, err := base64.StdEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	if b, err := base64.RawStdEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	if b, err := base64.URLEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	if b, err := base64.RawURLEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	return nil, errors.New("not valid base64 or hex")
}

// isHex reports whether s is a non-empty, even-length hex string.
func isHex(s string) bool {
	if len(s) == 0 || len(s)%2 != 0 {
		return false
	}
	for _, c := range s {
		if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
			return false
		}
	}
	return true
}

// CanonicalJSON produces a deterministic JSON encoding with sorted keys so the
// signature is stable across platforms (Kotlin/Swift/Go must agree).
func CanonicalJSON(v any) ([]byte, error) {
	var buf strings.Builder
	if err := writeCanonical(&buf, v); err != nil {
		return nil, err
	}
	return []byte(buf.String()), nil
}

func writeCanonical(buf *strings.Builder, v any) error {
	switch t := v.(type) {
	case nil:
		buf.WriteString("null")
	case bool:
		if t {
			buf.WriteString("true")
		} else {
			buf.WriteString("false")
		}
	case float64:
		buf.WriteString(json.Number(formatFloat(t)).String())
	case json.Number:
		buf.WriteString(t.String())
	case string:
		b, _ := json.Marshal(t)
		buf.Write(b)
	case []any:
		buf.WriteByte('[')
		for i, e := range t {
			if i > 0 {
				buf.WriteByte(',')
			}
			if err := writeCanonical(buf, e); err != nil {
				return err
			}
		}
		buf.WriteByte(']')
	case map[string]any:
		keys := make([]string, 0, len(t))
		for k := range t {
			keys = append(keys, k)
		}
		sort.Strings(keys)
		buf.WriteByte('{')
		for i, k := range keys {
			if i > 0 {
				buf.WriteByte(',')
			}
			kb, _ := json.Marshal(k)
			buf.Write(kb)
			buf.WriteByte(':')
			if err := writeCanonical(buf, t[k]); err != nil {
				return err
			}
		}
		buf.WriteByte('}')
	default:
		// Fall back to marshalling then re-canonicalising.
		raw, err := json.Marshal(v)
		if err != nil {
			return err
		}
		var generic any
		if err := json.Unmarshal(raw, &generic); err != nil {
			return err
		}
		return writeCanonical(buf, generic)
	}
	return nil
}

// formatFloat renders a float deterministically to match the Kotlin and
// TypeScript canonical encoders. Integral values keep a ".0" suffix (Kotlin
// Double.toString() and JS Number.toString() both do this for e.g. 52.0 -> "52"
// in JS but "52.0" in Kotlin; we standardise on the Kotlin/Go-float form).
func formatFloat(f float64) string {
	if f == math.Trunc(f) && !math.IsInf(f, 0) {
		// Preserve a single decimal place for integral values so all platforms
		// agree: 52 -> "52.0".
		return strconv.FormatFloat(f, 'f', 1, 64)
	}
	return strconv.FormatFloat(f, 'f', -1, 64)
}
