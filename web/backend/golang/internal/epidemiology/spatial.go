// Package epidemiology implements spatial statistics for disease spread.
//
// Mirrors the Python engine (app/services/physics/bayesian.py) so the Go
// orchestration can classify a spatial pattern before it enriches the Bayesian
// call, keeping the two in agreement.
package epidemiology

import (
	"math"
	"sort"
)

// Point is a geolocated observation with a severity value.
type Point struct {
	Lon      float64
	Lat      float64
	Severity float64
}

// HaversineM returns the great-circle distance in metres between two points.
func HaversineM(lat1, lon1, lat2, lon2 float64) float64 {
	const r = 6371000.0
	dLat := (lat2 - lat1) * math.Pi / 180.0
	dLon := (lon2 - lon1) * math.Pi / 180.0
	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Cos(lat1*math.Pi/180)*math.Cos(lat2*math.Pi/180)*
			math.Sin(dLon/2)*math.Sin(dLon/2)
	return 2 * r * math.Asin(math.Sqrt(a))
}

// MoransI computes spatial autocorrelation with a distance-band weight matrix.
// I > 0 clustered (contagious), ~0 random, < 0 dispersed.
func MoransI(pts []Point, bandwidthM float64) float64 {
	n := len(pts)
	if n < 2 {
		return 0.0
	}
	mean := 0.0
	for _, p := range pts {
		mean += p.Severity
	}
	mean /= float64(n)
	dev := make([]float64, n)
	for i, p := range pts {
		dev[i] = p.Severity - mean
	}
	wSum, num := 0.0, 0.0
	for i := 0; i < n; i++ {
		for j := 0; j < n; j++ {
			if i == j {
				continue
			}
			d := HaversineM(pts[i].Lat, pts[i].Lon, pts[j].Lat, pts[j].Lon)
			if d <= bandwidthM && d > 0 {
				w := 1.0 / d
				wSum += w
				num += w * dev[i] * dev[j]
			}
		}
	}
	den := 0.0
	for _, d := range dev {
		den += d * d
	}
	if wSum == 0 || den == 0 {
		return 0.0
	}
	return (float64(n) / wSum) * (num / den)
}

// EdgeBias measures how much infection clusters near the land edge.
// points are observations; edgeDistancesM[i] is point i's distance to edge.
// Returns a fraction in [0,1]: share of total severity carried by points within
// `band` metres of the edge.
func EdgeBias(points []Point, edgeDistancesM []float64, band float64) float64 {
	if len(points) != len(edgeDistancesM) || len(points) == 0 {
		return 0.0
	}
	total := 0.0
	near := 0.0
	for i, p := range points {
		total += p.Severity
		if edgeDistancesM[i] >= 0 && edgeDistancesM[i] <= band {
			near += p.Severity
		}
	}
	if total == 0 {
		return 0.0
	}
	return near / total
}

// TroughBias measures how much infection clusters in wet (high-TWI) cells.
func TroughBias(points []Point, twiValues []float64, threshold float64) float64 {
	if len(points) != len(twiValues) || len(points) == 0 {
		return 0.0
	}
	total, wet := 0.0, 0.0
	for i, p := range points {
		total += p.Severity
		if twiValues[i] >= threshold {
			wet += p.Severity
		}
	}
	if total == 0 {
		return 0.0
	}
	return wet / total
}

// ClassifyPattern names the outbreak shape. Mirrors the Python classifier.
func ClassifyPattern(moransI, edgeBias, troughBias float64) string {
	if moransI <= 0.05 {
		return "UNIFORM_BLOCK"
	}
	if troughBias >= edgeBias && troughBias >= 0.5 {
		return "DRAINAGE_TROUGH_CLUSTER"
	}
	if edgeBias >= troughBias && edgeBias >= 0.5 {
		return "EDGE_WIND_GRADIENT"
	}
	return "CLUSTERED_UNSPECIFIED"
}

// PatternFromPoints is a convenience that runs the full classification.
func PatternFromPoints(pts []Point, edgeDistancesM, twiValues []float64,
	bandwidthM, edgeBand, twiThreshold float64) (string, MoranResult) {
	mi := MoransI(pts, bandwidthM)
	eb := EdgeBias(pts, edgeDistancesM, edgeBand)
	tb := TroughBias(pts, twiValues, twiThreshold)
	return ClassifyPattern(mi, eb, tb), MoranResult{
		MoransI: mi, EdgeBias: eb, TroughBias: tb,
	}
}

// MoranResult bundles the spatial statistics.
type MoranResult struct {
	MoransI    float64
	EdgeBias   float64
	TroughBias float64
}

// SortedSeverities returns severities ascending (handy for tests/debug).
func SortedSeverities(pts []Point) []float64 {
	out := make([]float64, len(pts))
	for i, p := range pts {
		out[i] = p.Severity
	}
	sort.Float64s(out)
	return out
}
