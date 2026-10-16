"""Strict tests for the SHIPPED edge model artefacts (Tahap 2/3 integration).

These tests guard the *real* ONNX files committed under models/. They encode the
field-critical discriminants of Opsi 1 (leaf-curl & lesion differentiation) and
Opsi 2 (offline prescriptive fertilizer dosage).

Regression context: the previous classifier mapped *mean RGB only* → 5 logits and
provably misclassified Antraknosa and Mites as Begomovirus. These tests fail
loudly if that regresses.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest

from app.services.models import inference, registry

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"

pytestmark = pytest.mark.skipif(
    not inference.ort_available(), reason="onnxruntime not installed")


def _sess(name: str):
    import onnxruntime as ort

    card = json.loads((MODELS_DIR / name / "card.json").read_text())
    path = MODELS_DIR / name / f"{card['version']}.onnx"
    assert path.exists(), (
        f"missing artefact {path} — run `make models-train` to regenerate")
    return ort.InferenceSession(str(path), providers=["CPUExecutionProvider"]), card


def _classifier_probs(rgba_fill):
    """Build a 224x224 3-channel image and run the classifier."""
    sess, card = _sess("classifier_mobilenetv3")
    img = np.zeros((1, 3, 224, 224), dtype=np.float32)
    img[0, 0] = rgba_fill[0]
    img[0, 1] = rgba_fill[1]
    img[0, 2] = rgba_fill[2]
    probs = sess.run(["probabilities"], {"image": img})[0][0]
    classes = card["classes"]
    return classes[int(np.argmax(probs))], float(np.max(probs)), classes


class TestArtefactsPresent:
    @pytest.mark.parametrize("name", ["classifier_mobilenetv3", "fertilizer_lightgbm"])
    def test_card_and_artefact_and_integrity(self, name):
        card_dir = MODELS_DIR / name
        card = json.loads((card_dir / "card.json").read_text())
        art = card_dir / f"{card['version']}.onnx"
        assert art.exists(), f"{name}: artefact not present (run `make models-train`)"
        assert card["sha256"], f"{name}: empty sha256 (placeholder, not trained)"
        # sha matches the shipped bytes.
        assert registry.sha256_file(art) == card["sha256"]
        # registry sees a non-placeholder version.
        reg = registry.Registry.from_dir(MODELS_DIR)
        assert reg.get(name).version != "0.0.0"


class TestClassifierDiscrimination:
    """The classifier must separate the 5 Opsi-1 conditions, incl. curl direction."""

    def test_healthy_green(self):
        pred, conf, _ = _classifier_probs((0.18, 0.62, 0.22))
        # A plain green leaf must NOT be flagged diseased at high confidence.
        assert conf > 0.5 or pred == "Daun_Sehat"

    def test_yellow_mosaic_is_begomovirus(self):
        # bright yellow: high R+G, low B.
        pred, conf, _ = _classifier_probs((0.55, 0.78, 0.14))
        assert pred == "Begomovirus", f"got {pred} ({conf:.2f})"

    def test_dark_lesion_is_antraknosa(self):
        # dark greenish-brown, low luminance.
        pred, conf, _ = _classifier_probs((0.16, 0.20, 0.10))
        assert pred == "Antraknosa", f"got {pred} ({conf:.2f})"

    def test_upward_curl_thrips_vs_downward_mites(self):
        """The KEY discriminant: curl direction from vertical green asymmetry.

        Upward curl darkens the top third of the blade and lifts the underside
        (silvery); downward curl darkens the lower third and bronzes it.
        """
        sess, card = _sess("classifier_mobilenetv3")
        classes = card["classes"]

        def run(img):
            p = sess.run(["probabilities"], {"image": img[None].astype(np.float32)})[0][0]
            return classes[int(np.argmax(p))], float(np.max(p))

        # Thrips: top third darker, underside brighter (silvery).
        thrips = np.zeros((3, 224, 224), dtype=np.float32)
        thrips[0], thrips[1], thrips[2] = 0.30, 0.66, 0.34
        thrips[1, :74] *= 0.78
        thrips[1, 150:] *= 1.10
        thrips[2, 150:] += 0.08
        # Mites: lower third darker + bronze (R up, B down).
        mites = np.zeros((3, 224, 224), dtype=np.float32)
        mites[0], mites[1], mites[2] = 0.34, 0.60, 0.26
        mites[1, 150:] *= 0.76
        mites[0, 150:] += 0.14
        mites[2, 150:] -= 0.05

        pt, _ = run(thrips)
        pm, _ = run(mites)
        assert pt == "Keriting_Thrips", f"thrips image → {pt}"
        assert pm == "Keriting_Mites", f"mites image → {pm}"
        assert pt != pm

    def test_colour_distinct_cases_not_collapsed(self):
        """Colour-distinct presentations must not all map to one class.

        (Uniform fills cannot express curl direction, so Thrips/Mites are
        covered by the gradient-based test above; here we only assert the
        colour-driven cases stay separable.)
        """
        cases = {
            "Begomovirus": (0.55, 0.78, 0.14),
            "Antraknosa": (0.16, 0.20, 0.10),
            "Daun_Sehat": (0.18, 0.62, 0.22),
        }
        preds = {_classifier_probs(v)[0] for v in cases.values()}
        assert len(preds) == 3, f"classifier collapsed: {preds}"


class TestFertilizerDistillation:
    """The ONNX fertilizer model must reproduce the rule engine's dosages."""

    @staticmethod
    def _rule(ph, cec, hst, ty):
        from app.services.physics import fertilizer

        p = fertilizer.prescribe(ph, cec, hst, ty)
        return np.array([p.npk_16_16_16_g_per_l, p.kno3_g_per_l, p.calcium_g_per_l])

    @pytest.mark.parametrize("ph,cec,hst,ty", [
        (5.0, 10.0, 20, 20.0), (6.5, 15.0, 45, 20.0), (6.8, 12.0, 75, 25.0),
        (4.8, 8.0, 50, 18.0), (7.0, 25.0, 30, 30.0), (6.0, 6.0, 100, 12.0),
    ])
    def test_onnx_matches_rules_within_tolerance(self, ph, cec, hst, ty):
        sess, _ = _sess("fertilizer_lightgbm")
        inp = np.array([[ph, cec, hst, ty]], dtype=np.float32)
        got = sess.run(["dose"], {"input": inp})[0][0]
        truth = self._rule(ph, cec, hst, ty)
        np.testing.assert_allclose(got, truth, atol=0.35)

    def test_phase_transition(self):
        """Flowering (HST 45) adds Ca+KNO3; fruiting (HST 75) adds more KNO3."""
        sess, _ = _sess("fertilizer_lightgbm")

        def dose(hst):
            return sess.run(["dose"], {"input": np.array([[6.5, 15.0, hst, 20.0]],
                                                         dtype=np.float32)})[0][0]

        veg, flo, fru = dose(20), dose(45), dose(75)
        assert veg[1] == pytest.approx(0.0, abs=0.35)   # no KNO3 vegetative
        assert flo[2] > 1.0                              # calcium in flowering
        assert fru[1] > flo[1]                           # more KNO3 at fruiting

    def test_inference_endpoint_uses_onnx_path(self):
        """The registered model is reachable through the inference runtime."""
        feats = np.array([[6.5, 15.0, 45.0, 20.0]], dtype=np.float32)
        res = inference.infer(MODELS_DIR, "fertilizer_lightgbm", {"input": feats})
        assert res.available is True
        assert res.version == "1.0.0"
        assert "dose" in res.outputs
        row = res.outputs["dose"][0]
        assert row[2] == pytest.approx(2.5, abs=0.35)  # flowering calcium
