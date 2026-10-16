"""Tests for the severity segmentation model + service (Opsi 1, severity).

`severity_pct` scales the fungicide dose, so the ONNX segmenter
(severity_unet_lite) and its deterministic fallback must agree on the shape of
the signal: dark necrotic patches raise severity, yellow chlorosis raises it
half as much, and a clean green leaf yields near-zero severity.

The model is a 4-class pixel segmenter [healthy, chlorotic, necrotic,
background]; severity = necrotic + 0.5*chlorotic over the leaf region.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest

from app.services.agronomy import severity
from app.services.models import registry

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"
ART = MODELS_DIR / "severity_unet_lite"


def _green_leaf() -> np.ndarray:
    """A green leaf filling most of the frame (as the field app crops it)."""
    rng = np.random.default_rng(0)
    img = np.zeros((3, 256, 256), dtype=np.float32)
    img[0], img[1], img[2] = 0.18, 0.62, 0.24
    img += rng.normal(0, 0.02, img.shape).astype(np.float32)
    return np.clip(img, 0, 1)


def _paint_lesion(img: np.ndarray, r0, r1, c0, c1, seed=0) -> np.ndarray:
    """Paint a *textured* brown sunken lesion (as real necrosis looks)."""
    rng = np.random.default_rng(seed)
    out = img.copy()
    out[0, r0:r1, c0:c1] *= 0.55
    out[1, r0:r1, c0:c1] *= 0.35
    out[2, r0:r1, c0:c1] *= 0.40
    out[0, r0:r1, c0:c1] += 0.05  # brown tint (R up)
    out[:, r0:r1, c0:c1] += rng.normal(0, 0.05, (3, r1 - r0, c1 - c0)).astype(np.float32)
    return np.clip(out, 0, 1)


def _with_necrotic(img: np.ndarray, frac=0.15) -> np.ndarray:
    """A textured brown lesion covering a sub-region of the leaf."""
    return _paint_lesion(img, 100, 140, 100, 140)


def _with_chlorotic(img: np.ndarray) -> np.ndarray:
    out = img.copy()
    k = 40
    out[0, 60:60 + k, 60:60 + k] = 0.72  # bright yellow
    out[1, 60:60 + k, 60:60 + k] = 0.78
    out[2, 60:60 + k, 60:60 + k] = 0.12
    return out


class TestSeverityArtefact:
    def test_card_and_artefact_integrity(self):
        card = json.loads((ART / "card.json").read_text())
        art = ART / f"{card['version']}.onnx"
        assert art.exists(), "severity artefact not present (run `make models-train`)"
        assert card["sha256"], "empty sha256 (placeholder, not trained)"
        assert registry.sha256_file(art) == card["sha256"]
        assert registry.Registry.from_dir(MODELS_DIR).get(
            "severity_unet_lite").version != "0.0.0"

    def test_output_is_4_class(self):
        card = json.loads((ART / "card.json").read_text())
        assert card["classes"] == ["healthy", "chlorotic", "necrotic", "background"]


class TestSeveritySignal:
    """Monotonicity & sanity of the severity signal (ONNX path)."""

    def test_healthy_leaf_low_severity(self):
        res = severity.assess_severity(_green_leaf(), models_dir=MODELS_DIR)
        assert res.method == "onnx_model"
        assert res.severity_pct < 5.0, f"healthy leaf severity {res.severity_pct}"

    def test_necrotic_raises_severity(self):
        # A large textured brown lesion must dominate the leaf's severity.
        img = _paint_lesion(_green_leaf(), 70, 180, 70, 180)
        res = severity.assess_severity(img, models_dir=MODELS_DIR)
        assert res.necrotic_pct > res.chlorotic_pct
        assert res.severity_pct > 8.0, f"severity {res.severity_pct}"

    def test_chlorosis_counts_half(self):
        # Chlorotic patch → chlorotic_pct high, necrotic low.
        res = severity.assess_severity(_with_chlorotic(_green_leaf()),
                                       models_dir=MODELS_DIR)
        assert res.chlorotic_pct > res.necrotic_pct
        # severity must be ≈ 0.5 * chlorotic (half-weight semantics).
        assert res.severity_pct == pytest.approx(0.5 * res.chlorotic_pct, abs=1.5)

    def test_monotone_more_lesion_more_severity(self):
        base = _green_leaf()
        small = _paint_lesion(base, 120, 150, 120, 150, seed=1)
        big = _paint_lesion(base, 80, 190, 80, 190, seed=2)
        s = severity.assess_severity(small, models_dir=MODELS_DIR).severity_pct
        b = severity.assess_severity(big, models_dir=MODELS_DIR).severity_pct
        assert b > s, f"bigger lesion ({b}) not > smaller ({s})"


class TestFallback:
    """The deterministic fallback must produce a comparable signal shape."""

    def _no_onnx_dir(self, tmp_path):
        # empty registry dir → inference returns available=False → fallback.
        return tmp_path / "empty_models"

    def test_fallback_runs_and_is_labelled(self, tmp_path):
        (tmp_path / "empty_models").mkdir()
        res = severity.assess_severity(_with_necrotic(_green_leaf()),
                                       models_dir=tmp_path / "empty_models")
        assert res.method == "deterministic_fallback"
        assert 0.0 <= res.severity_pct <= 100.0

    def test_fallback_necrotic_beats_healthy(self, tmp_path):
        d = tmp_path / "m"
        d.mkdir()
        h = severity.assess_severity(_green_leaf(), models_dir=d)
        n = severity.assess_severity(_with_necrotic(_green_leaf()), models_dir=d)
        assert n.severity_pct > h.severity_pct

    def test_input_validation(self):
        with pytest.raises(ValueError):
            severity.assess_severity(np.zeros((10, 10), dtype=np.float32),
                                     models_dir=MODELS_DIR)


class TestEndpointShape:
    def test_endpoint_returns_expected_fields(self):
        """The service result maps onto the API response contract."""
        res = severity.assess_severity(_with_necrotic(_green_leaf()),
                                       models_dir=MODELS_DIR)
        d = res.__dict__
        for key in ("severity_pct", "chlorotic_pct", "necrotic_pct",
                    "healthy_pct", "method", "uncertain_pct"):
            assert key in d, key
        # parts sum to ~100 on the leaf region.
        assert res.chlorotic_pct + res.necrotic_pct + res.healthy_pct == pytest.approx(
            100.0, abs=0.5)
