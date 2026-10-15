"""Strict tests: image forensics (ELA, EXIF, perceptual-hash duplicates)."""
from __future__ import annotations

import io

import numpy as np
import pytest
from PIL import Image

from app.services.epidemiology import image_forensics as fx


def _jpeg_with_exif(seed: int = 0, size: int = 64) -> bytes:
    rng = np.random.default_rng(seed)
    arr = rng.integers(0, 255, (size, size, 3), dtype=np.uint8)
    img = Image.fromarray(arr, "RGB")
    exif = img.getexif()
    exif[271] = "TestMake"      # Make
    exif[272] = "TestModel"     # Model
    exif[36867] = "2026:10:15 09:00:00"  # DateTimeOriginal
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=95, exif=exif)
    return buf.getvalue()


def _jpeg_no_exif(seed: int = 0, size: int = 64) -> bytes:
    rng = np.random.default_rng(seed)
    arr = rng.integers(0, 255, (size, size, 3), dtype=np.uint8)
    buf = io.BytesIO()
    Image.fromarray(arr, "RGB").save(buf, format="JPEG", quality=95)
    return buf.getvalue()


class TestExif:
    def test_detects_make_model_time(self):
        exif = fx.extract_exif(_jpeg_with_exif())
        assert "Model" in exif or "Make" in exif

    def test_missing_exif_on_bare_jpeg(self):
        exif = fx.extract_exif(_jpeg_no_exif())
        assert "Model" not in exif


class TestAvgHash:
    def test_identical_images_same_hash(self):
        raw = _jpeg_with_exif()
        gray = np.asarray(Image.open(io.BytesIO(raw)).convert("L"))
        assert fx._avg_hash(gray) == fx._avg_hash(gray)

    def test_different_images_different_hash(self):
        g1 = np.asarray(Image.open(io.BytesIO(_jpeg_with_exif(1))).convert("L"))
        g2 = np.asarray(Image.open(io.BytesIO(_jpeg_with_exif(2))).convert("L"))
        assert fx._avg_hash(g1) != fx._avg_hash(g2)

    def test_hamming_distance(self):
        assert fx.hamming_distance_hex("0000000000000000", "0000000000000000") == 0
        assert fx.hamming_distance_hex("0000000000000000", "000000000000000f") == 4
        assert fx.hamming_distance_hex("ffffffffffffffff", "0000000000000000") == 64


class TestEla:
    def test_ela_variance_non_negative(self):
        assert fx.ela_variance(_jpeg_with_exif()) >= 0.0

    def test_uniform_image_low_ela(self):
        img = Image.fromarray(np.full((64, 64, 3), 128, dtype=np.uint8), "RGB")
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=95)
        # A perfectly uniform image recompresses with almost no error.
        assert fx.ela_variance(buf.getvalue()) < 5.0


class TestForensicAudit:
    def test_clean_photo_passes(self):
        res = fx.forensic_audit(_jpeg_with_exif())
        assert res.exif_ok is True
        assert res.duplicate_of is None
        assert res.verdict in ("PASS", "REVIEW (weak provenance)")

    def test_duplicate_rejected(self):
        raw = _jpeg_with_exif()
        h = fx.forensic_audit(raw).perceptual_hash
        res = fx.forensic_audit(raw, known_hashes=[h])
        assert res.duplicate_of == h
        assert "duplicate_image" in res.flags
        assert res.verdict.startswith("REJECT")

    def test_missing_exif_flagged(self):
        res = fx.forensic_audit(_jpeg_no_exif())
        assert res.exif_ok is False
        assert "missing_camera_model" in res.flags

    def test_distinct_photo_not_duplicate(self):
        raw = _jpeg_with_exif(1)
        other_hash = fx.forensic_audit(_jpeg_with_exif(99)).perceptual_hash
        res = fx.forensic_audit(raw, known_hashes=[other_hash])
        assert res.duplicate_of is None
