package worker

import "testing"

func TestGDDStep(t *testing.T) {
	cases := []struct {
		tMin, tMax, want float64
	}{
		{20, 30, 10}, // (30+20)/2 - 15 = 10
		{10, 12, 0},  // clamped below base
		{15, 15, 0},  // exactly base
		{25, 35, 15}, // (35+25)/2 - 15 = 15
	}
	for _, c := range cases {
		if got := gddStep(c.tMin, c.tMax); got != c.want {
			t.Errorf("gddStep(%v,%v) = %v, want %v", c.tMin, c.tMax, got, c.want)
		}
	}
}

func TestTodayTempsPlausible(t *testing.T) {
	tMin, tMax := todayTemps()
	if tMin < 15 || tMin > 25 {
		t.Errorf("tMin %v out of plausible range", tMin)
	}
	if tMax < 25 || tMax > 40 {
		t.Errorf("tMax %v out of plausible range", tMax)
	}
	if tMax <= tMin {
		t.Errorf("tMax %v must exceed tMin %v", tMax, tMin)
	}
}

func TestGDDStepMonotonic(t *testing.T) {
	// Higher max temperature for the same min => larger or equal step.
	a := gddStep(20, 28)
	b := gddStep(20, 32)
	if b <= a {
		t.Fatalf("hotter day should give >= gdd: %v vs %v", b, a)
	}
}
