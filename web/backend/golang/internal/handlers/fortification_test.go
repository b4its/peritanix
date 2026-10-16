package handlers

import "testing"

func TestNDominant(t *testing.T) {
	cases := []struct {
		in   fertilizerInput
		want bool
	}{
		{fertilizerInput{Product: "Urea"}, true},
		{fertilizerInput{Product: "urea 46%"}, true},
		{fertilizerInput{Product: "ZA (Ammonium Sulfat)"}, true},
		{fertilizerInput{Product: "Ammonium Nitrate"}, true},
		{fertilizerInput{Product: "NPK 16-16-16"}, false},
		{fertilizerInput{Product: "KNO3"}, false},
		{fertilizerInput{Product: "Kalsium Nitrat"}, false},
		{fertilizerInput{Product: "Pupuk Kandang"}, false},
		// Nitrogen percentage override.
		{fertilizerInput{Product: "Mystery", NitrogenPct: 46}, true},
		{fertilizerInput{Product: "Mystery", NitrogenPct: 16}, false},
		// NPK ratio override.
		{fertilizerInput{Product: "Mystery", NPKRatio: "46-0-0"}, true},
		{fertilizerInput{Product: "Mystery", NPKRatio: "15-15-15"}, false},
		{fertilizerInput{Product: "Mystery", NPKRatio: "30-0-0"}, true},
	}
	for _, c := range cases {
		if got := nDominant(c.in); got != c.want {
			t.Errorf("nDominant(%+v) = %v, want %v", c.in, got, c.want)
		}
	}
}

func TestNDominantCaseInsensitive(t *testing.T) {
	if !nDominant(fertilizerInput{Product: "UREA"}) {
		t.Fatal("uppercase UREA should be N-dominant")
	}
	if !nDominant(fertilizerInput{Product: "  Urea  "}) {
		t.Fatal("whitespace-padded Urea should be N-dominant")
	}
}
