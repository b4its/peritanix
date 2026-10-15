// Package crdt implements conflict resolution for offline multi-worker edits.
//
// DAPE uses a hybrid strategy per the design doc:
//
//   - **LWW-Element-Set** semantics by default: each field carries an
//     (updated_ts, rev, device_id) triple; the highest wins. Ties are broken
//     deterministically by rev then device_id so all replicas converge.
//   - **Version vectors** to detect concurrency: two edits are concurrent if
//     neither vector dominates the other. Concurrent field-level edits are
//     merged field-by-field, not whole-record (last-writer-wins-per-field).
//
// This is a *correct*, convergent merge in the CRDT sense: applying merges in
// any order yields the same result (commutativity + idempotence + associativity).
package crdt

import (
	"sort"
	"strings"
)

// VersionVector counts per-device update counts.
type VersionVector map[string]uint64

// Clone returns a copy.
func (v VersionVector) Clone() VersionVector {
	out := make(VersionVector, len(v))
	for k, val := range v {
		out[k] = val
	}
	return out
}

// Observe bumps the counter for a device.
func (v VersionVector) Observe(device string) {
	v[device]++
}

// Merge returns the component-wise max of two vectors.
func (v VersionVector) Merge(o VersionVector) VersionVector {
	out := v.Clone()
	for k, val := range o {
		if val > out[k] {
			out[k] = val
		}
	}
	return out
}

// Dominates reports whether v >= o (v saw everything o saw).
func (v VersionVector) Dominates(o VersionVector) bool {
	for k, val := range o {
		if v[k] < val {
			return false
		}
	}
	return true
}

// Concurrent reports whether neither vector dominates (true concurrency).
func Concurrent(a, b VersionVector) bool {
	return !a.Dominates(b) && !b.Dominates(a)
}

// Timestamp is the per-field LWW clock.
type Timestamp struct {
	UpdatedTS int64  `json:"updated_ts"`
	Rev       int64  `json:"rev"`
	DeviceID  string `json:"device_id"`
}

// After reports whether t should win over o (strict LWW ordering).
func (t Timestamp) After(o Timestamp) bool {
	if t.UpdatedTS != o.UpdatedTS {
		return t.UpdatedTS > o.UpdatedTS
	}
	if t.Rev != o.Rev {
		return t.Rev > o.Rev
	}
	// Deterministic tie-break so all replicas converge.
	return t.DeviceID > o.DeviceID
}

// FieldValue pairs a value with its LWW timestamp.
type FieldValue struct {
	Value     any       `json:"value"`
	Timestamp Timestamp `json:"ts"`
}

// Record is a version-vectored map with per-field LWW timestamps.
type Record struct {
	ID     string                `json:"id"`
	VV     VersionVector         `json:"vv"`
	Fields map[string]FieldValue `json:"fields"`
}

// NewRecord creates an empty record.
func NewRecord(id string) *Record {
	return &Record{ID: id, VV: VersionVector{}, Fields: map[string]FieldValue{}}
}

// Set writes a field with the given timestamp, advancing the version vector.
func (r *Record) Set(device string, field string, value any, ts Timestamp) {
	ts.DeviceID = device
	if cur, ok := r.Fields[field]; ok {
		if !ts.After(cur.Timestamp) {
			return // incoming is older; keep current
		}
	}
	r.Fields[field] = FieldValue{Value: value, Timestamp: ts}
	r.VV.Observe(device)
}

// Merge combines two records field-by-field using LWW. The result's version
// vector is the component-wise max. This operation is commutative, associative
// and idempotent: merge(a,b) == merge(b,a), merge(a,a) == a.
func Merge(a, b *Record) *Record {
	if a == nil {
		return cloneRecord(b)
	}
	if b == nil {
		return cloneRecord(a)
	}
	out := NewRecord(a.ID)
	if out.ID == "" {
		out.ID = b.ID
	}
	out.VV = a.VV.Merge(b.VV)

	// Union of field keys.
	keys := map[string]struct{}{}
	for k := range a.Fields {
		keys[k] = struct{}{}
	}
	for k := range b.Fields {
		keys[k] = struct{}{}
	}

	for k := range keys {
		fa, oka := a.Fields[k]
		fb, okb := b.Fields[k]
		switch {
		case oka && !okb:
			out.Fields[k] = fa
		case okb && !oka:
			out.Fields[k] = fb
		default:
			if fb.Timestamp.After(fa.Timestamp) {
				out.Fields[k] = fb
			} else {
				out.Fields[k] = fa
			}
		}
	}
	return out
}

func cloneRecord(r *Record) *Record {
	if r == nil {
		return nil
	}
	out := NewRecord(r.ID)
	out.VV = r.VV.Clone()
	for k, v := range r.Fields {
		out.Fields[k] = v
	}
	return out
}

// FieldKeys returns the sorted field names (for deterministic output).
func (r *Record) FieldKeys() []string {
	keys := make([]string, 0, len(r.Fields))
	for k := range r.Fields {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}

// VVString renders the version vector deterministically (device:count,...).
func (v VersionVector) VVString() string {
	keys := make([]string, 0, len(v))
	for k := range v {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, k := range keys {
		parts = append(parts, k+":"+itoa(v[k]))
	}
	return strings.Join(parts, ",")
}

func itoa(v uint64) string {
	if v == 0 {
		return "0"
	}
	var b [20]byte
	i := len(b)
	for v > 0 {
		i--
		b[i] = byte('0' + v%10)
		v /= 10
	}
	return string(b[i:])
}
