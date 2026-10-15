package handlers

import "testing"

func TestSafeFilename(t *testing.T) {
	cases := []struct{ in, want string }{
		{"frame0.jpg", "frame0.jpg"},
		{"../../etc/passwd", "passwd"},
		{"/abs/path/img.png", "img.png"},
		{"a\\b\\c.png", "c.png"},
		{"weird name!@#.jpg", "weirdname.jpg"},
		{"..", ""},
		{".", ""},
		{"", ""},
		{"normal_2024-10-15.mp4", "normal_2024-10-15.mp4"},
	}
	for _, c := range cases {
		if got := safeFilename(c.in); got != c.want {
			t.Errorf("safeFilename(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestSafeFilenameLengthBounded(t *testing.T) {
	long := ""
	for i := 0; i < 500; i++ {
		long += "a"
	}
	got := safeFilename(long)
	if len(got) > 120 {
		t.Fatalf("filename too long: %d", len(got))
	}
}
