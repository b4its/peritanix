package crdt

import (
	"reflect"
	"testing"
)

func ts(updated, rev int64, dev string) Timestamp {
	return Timestamp{UpdatedTS: updated, Rev: rev, DeviceID: dev}
}

// --- Timestamp ordering -----------------------------------------------------

func TestTimestampAfter(t *testing.T) {
	base := ts(100, 1, "devA")
	if !ts(101, 1, "devA").After(base) {
		t.Fatal("higher ts should win")
	}
	if base.After(ts(101, 1, "devA")) {
		t.Fatal("lower ts must not win")
	}
	// tie on updated -> rev decides
	if !ts(100, 2, "devA").After(base) {
		t.Fatal("higher rev should win on ts tie")
	}
	// tie on updated & rev -> device id decides deterministically
	if !ts(100, 1, "devB").After(base) {
		t.Fatal("device tie-break should be deterministic")
	}
	if ts(100, 1, "devA").After(base) {
		t.Fatal("equal timestamps: not strictly after")
	}
}

// --- Version vectors --------------------------------------------------------

func TestVersionVectorDominance(t *testing.T) {
	a := VersionVector{"x": 2, "y": 1}
	b := VersionVector{"x": 1, "y": 1}
	if !a.Dominates(b) {
		t.Fatal("a should dominate b")
	}
	if b.Dominates(a) {
		t.Fatal("b must not dominate a")
	}
	if Concurrent(a, b) {
		t.Fatal("a dominating b means they are NOT concurrent")
	}
	c := VersionVector{"x": 1, "z": 1}
	if !Concurrent(a, c) {
		t.Fatal("a and c are concurrent")
	}
}

func TestVersionVectorMergeIsMax(t *testing.T) {
	a := VersionVector{"x": 2, "y": 1}
	b := VersionVector{"x": 1, "z": 5}
	m := a.Merge(b)
	want := VersionVector{"x": 2, "y": 1, "z": 5}
	if !reflect.DeepEqual(m, want) {
		t.Fatalf("merge = %v, want %v", m, want)
	}
}

// --- Record LWW -------------------------------------------------------------

func TestSetKeepsNewer(t *testing.T) {
	r := NewRecord("land-1")
	r.Set("devA", "ph", 6.5, ts(100, 1, "devA"))
	r.Set("devA", "ph", 5.0, ts(50, 1, "devA")) // older -> ignored
	if r.Fields["ph"].Value != 6.5 {
		t.Fatalf("older write must not overwrite: got %v", r.Fields["ph"].Value)
	}
}

// --- CRDT laws --------------------------------------------------------------

func TestMergeCommutative(t *testing.T) {
	a := NewRecord("r")
	a.Set("devA", "ph", 6.5, ts(100, 1, "devA"))
	a.Set("devA", "n", 20.0, ts(100, 1, "devA"))
	b := NewRecord("r")
	b.Set("devB", "ph", 5.4, ts(120, 1, "devB")) // newer ph
	b.Set("devB", "k", 30.0, ts(110, 1, "devB"))

	ab := Merge(a, b)
	ba := Merge(b, a)
	if !reflect.DeepEqual(ab.Fields, ba.Fields) {
		t.Fatalf("merge not commutative:\n ab=%v\n ba=%v", ab.Fields, ba.Fields)
	}
	if !reflect.DeepEqual(ab.VV, ba.VV) {
		t.Fatal("version vectors differ")
	}
	// b's newer ph must win
	if ab.Fields["ph"].Value != 5.4 {
		t.Fatalf("newer ph should win: got %v", ab.Fields["ph"].Value)
	}
}

func TestMergeAssociative(t *testing.T) {
	mk := func(dev, field string, val any, u int64) *Record {
		r := NewRecord("r")
		r.Set(dev, field, val, ts(u, 1, dev))
		return r
	}
	a := mk("devA", "ph", 6.0, 100)
	b := mk("devB", "ph", 6.5, 120)
	c := mk("devC", "n", 25.0, 110)

	left := Merge(Merge(a, b), c)
	right := Merge(a, Merge(b, c))
	if !reflect.DeepEqual(left.Fields, right.Fields) {
		t.Fatalf("merge not associative:\n left=%v\n right=%v", left.Fields, right.Fields)
	}
}

func TestMergeIdempotent(t *testing.T) {
	a := NewRecord("r")
	a.Set("devA", "ph", 6.5, ts(100, 1, "devA"))
	a.Set("devA", "n", 20.0, ts(100, 1, "devA"))
	m := Merge(a, a)
	if !reflect.DeepEqual(m.Fields, a.Fields) {
		t.Fatalf("merge must be idempotent: %v vs %v", m.Fields, a.Fields)
	}
}

func TestMergeNil(t *testing.T) {
	a := NewRecord("r")
	a.Set("devA", "ph", 6.5, ts(100, 1, "devA"))
	if got := Merge(nil, a); !reflect.DeepEqual(got.Fields, a.Fields) {
		t.Fatal("merge(nil,a) should equal a")
	}
	if got := Merge(a, nil); !reflect.DeepEqual(got.Fields, a.Fields) {
		t.Fatal("merge(a,nil) should equal a")
	}
}

// A concrete scenario from the design doc: two field workers edit the SAME
// land's soil while offline, then both sync. Field-level merge must preserve
// both non-conflicting edits and pick the newer of the conflicting one.
func TestTwoWorkersConcurrentFieldMerge(t *testing.T) {
	workerA := NewRecord("land-x")
	workerA.Set("workerA", "ph", 6.5, ts(1000, 3, "workerA"))
	workerA.Set("workerA", "cec_meq", 15.0, ts(1000, 3, "workerA"))

	workerB := NewRecord("land-x")
	workerB.Set("workerB", "ph", 5.8, ts(1100, 1, "workerB")) // newer ph
	workerB.Set("workerB", "nitrate_ppm", 22.0, ts(1050, 1, "workerB"))

	merged := Merge(workerA, workerB)
	if merged.Fields["cec_meq"].Value != 15.0 {
		t.Fatal("non-conflicting cec_meq must survive")
	}
	if merged.Fields["nitrate_ppm"].Value != 22.0 {
		t.Fatal("non-conflicting nitrate_ppm must survive")
	}
	if merged.Fields["ph"].Value != 5.8 {
		t.Fatalf("newer ph (workerB) must win: got %v", merged.Fields["ph"].Value)
	}
	if merged.VV["workerA"] != 2 || merged.VV["workerB"] != 2 {
		t.Fatalf("version vector should track both workers: %v", merged.VV)
	}
}
