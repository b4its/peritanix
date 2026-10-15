package epidemiology

import (
	"math"
	"testing"
)

func approx(a, b, eps float64) bool { return math.Abs(a-b) <= eps }

func TestHaversineKnownDistance(t *testing.T) {
	// ~111 m for 0.001 deg latitude at the equator.
	d := HaversineM(0, 0, 0.001, 0)
	if !approx(d, 111.19, 1.0) {
		t.Fatalf("haversine = %v, want ~111.2", d)
	}
	// Same point -> zero.
	if HaversineM(1, 2, 1, 2) != 0 {
		t.Fatal("same point should be 0")
	}
}

func TestMoransIClusteredPositive(t *testing.T) {
	// A tight high cluster and a tight low cluster => positive autocorrelation.
	pts := []Point{
		{Lon: 0, Lat: 0, Severity: 10},
		{Lon: 0.0001, Lat: 0, Severity: 9.8},
		{Lon: 0, Lat: 0.0001, Severity: 10.2},
		{Lon: 0.0005, Lat: 0, Severity: 1.0},
		{Lon: 0, Lat: 0.0005, Severity: 1.2},
		{Lon: 0.0005, Lat: 0.0005, Severity: 0.8},
	}
	i := MoransI(pts, 200)
	if i <= 0 {
		t.Fatalf("expected positive Moran's I, got %v", i)
	}
}

func TestMoransISinglePointZero(t *testing.T) {
	if MoransI([]Point{{0, 0, 5}}, 50) != 0 {
		t.Fatal("single point -> 0")
	}
}

func TestMoransIConstantZero(t *testing.T) {
	pts := []Point{{0, 0, 5}, {0.0001, 0, 5}, {0, 0.0001, 5}}
	if MoransI(pts, 50) != 0 {
		t.Fatal("constant severities -> 0")
	}
}

func TestEdgeBias(t *testing.T) {
	pts := []Point{{Severity: 8}, {Severity: 2}}
	edges := []float64{2.0, 40.0} // first is near edge
	// 8 of 10 severity within band 5m -> 0.8
	if got := EdgeBias(pts, edges, 5.0); !approx(got, 0.8, 1e-9) {
		t.Fatalf("edge bias = %v, want 0.8", got)
	}
}

func TestTroughBias(t *testing.T) {
	pts := []Point{{Severity: 6}, {Severity: 4}}
	twi := []float64{9.0, 3.0}
	// 6 of 10 in high-TWI cell -> 0.6
	if got := TroughBias(pts, twi, 8.0); !approx(got, 0.6, 1e-9) {
		t.Fatalf("trough bias = %v, want 0.6", got)
	}
}

func TestClassifyPattern(t *testing.T) {
	if ClassifyPattern(0.4, 0.2, 0.7) != "DRAINAGE_TROUGH_CLUSTER" {
		t.Fatal("trough dominant")
	}
	if ClassifyPattern(0.4, 0.7, 0.2) != "EDGE_WIND_GRADIENT" {
		t.Fatal("edge dominant")
	}
	if ClassifyPattern(0.01, 0.2, 0.2) != "UNIFORM_BLOCK" {
		t.Fatal("no clustering")
	}
	if ClassifyPattern(0.4, 0.2, 0.3) != "CLUSTERED_UNSPECIFIED" {
		t.Fatal("clustered but ambiguous")
	}
}

func TestPatternFromPoints(t *testing.T) {
	// A dense block of high-severity points in wet troughs, far from the edge,
	// surrounded by a halo of low-severity points => clustered (trough) pattern.
	pts := []Point{
		{Lon: 0, Lat: 0, Severity: 9},
		{Lon: 0.0001, Lat: 0, Severity: 8.8},
		{Lon: 0, Lat: 0.0001, Severity: 9.1},
		{Lon: 0.0001, Lat: 0.0001, Severity: 8.9},
		{Lon: 0.0005, Lat: 0, Severity: 1.0},
		{Lon: 0, Lat: 0.0005, Severity: 1.2},
		{Lon: 0.0005, Lat: 0.0005, Severity: 0.9},
	}
	edges := make([]float64, len(pts))
	for i := range edges {
		edges[i] = 30 // all far from edge
	}
	twi := []float64{9.5, 9.3, 9.1, 9.4, 9.2, 9.0, 9.6} // all wet
	pattern, res := PatternFromPoints(pts, edges, twi, 200, 5.0, 8.0)
	if pattern != "DRAINAGE_TROUGH_CLUSTER" {
		t.Fatalf("pattern = %s (moran=%.3f edge=%.2f trough=%.2f)",
			pattern, res.MoransI, res.EdgeBias, res.TroughBias)
	}
}
