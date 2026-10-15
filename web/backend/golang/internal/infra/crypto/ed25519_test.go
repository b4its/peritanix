package crypto

import (
	"crypto/ed25519"
	"encoding/base64"
	"testing"
)

func sign(payload map[string]any) (sig, pub string) {
	seed := make([]byte, ed25519.SeedSize)
	for i := range seed {
		seed[i] = byte(i + 1)
	}
	priv := ed25519.NewKeyFromSeed(seed)
	pubk := priv.Public().(ed25519.PublicKey)
	msg, _ := CanonicalJSON(payload)
	s := ed25519.Sign(priv, msg)
	return base64.StdEncoding.EncodeToString(s), base64.StdEncoding.EncodeToString(pubk)
}

func TestCanonicalJSONIsOrderIndependent(t *testing.T) {
	a := map[string]any{"b": 2.0, "a": 1.0, "c": map[string]any{"z": true, "y": false}}
	b := map[string]any{"c": map[string]any{"y": false, "z": true}, "a": 1.0, "b": 2.0}
	ja, _ := CanonicalJSON(a)
	jb, _ := CanonicalJSON(b)
	if string(ja) != string(jb) {
		t.Fatalf("canonical JSON differs:\n%s\n%s", ja, jb)
	}
	if string(ja) != `{"a":1,"b":2,"c":{"y":false,"z":true}}` {
		t.Fatalf("unexpected canonical form: %s", ja)
	}
}

func TestVerifyValidSignature(t *testing.T) {
	payload := map[string]any{
		"land_id":             "aaa-111",
		"grade_result":        "A",
		"archimedes_sink_ratio": 0.82,
		"sterilization_c":     52.0,
	}
	sig, pub := sign(payload)
	res := Verify(Certificate{Payload: payload, Signature: sig, PublicKey: pub})
	if !res.Valid {
		t.Fatalf("expected valid, got: %s", res.Reason)
	}
}

func TestVerifyTamperedPayloadFails(t *testing.T) {
	payload := map[string]any{"land_id": "aaa-111", "grade_result": "A"}
	sig, pub := sign(payload)
	tampered := map[string]any{"land_id": "aaa-111", "grade_result": "BS"}
	res := Verify(Certificate{Payload: tampered, Signature: sig, PublicKey: pub})
	if res.Valid {
		t.Fatal("expected tampered payload to fail verification")
	}
}

func TestVerifyBadPublicKeyLength(t *testing.T) {
	res := Verify(Certificate{
		Payload:   map[string]any{"a": 1.0},
		Signature: base64.StdEncoding.EncodeToString(make([]byte, ed25519.SignatureSize)),
		PublicKey: base64.StdEncoding.EncodeToString([]byte("short")),
	})
	if res.Valid {
		t.Fatal("expected invalid public key length to fail")
	}
}

func TestVerifyHexEncodingAccepted(t *testing.T) {
	payload := map[string]any{"x": 1.0}
	seed := make([]byte, ed25519.SeedSize)
	priv := ed25519.NewKeyFromSeed(seed)
	pubk := priv.Public().(ed25519.PublicKey)
	msg, _ := CanonicalJSON(payload)
	s := ed25519.Sign(priv, msg)
	// hex-encode both
	sigHex := toHex(s)
	pubHex := toHex(pubk)
	res := Verify(Certificate{Payload: payload, Signature: sigHex, PublicKey: pubHex})
	if !res.Valid {
		t.Fatalf("expected hex-encoded cert to verify, got: %s", res.Reason)
	}
}

func toHex(b []byte) string {
	const hx = "0123456789abcdef"
	out := make([]byte, len(b)*2)
	for i, c := range b {
		out[i*2] = hx[c>>4]
		out[i*2+1] = hx[c&0x0f]
	}
	return string(out)
}
