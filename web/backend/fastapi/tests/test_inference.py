"""Strict tests: ONNX inference runtime with graceful fallback."""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest

from app.services.models import inference, registry


class TestFallback:
    def test_unknown_model_unavailable(self, tmp_path):
        res = inference.infer(tmp_path, "not_a_model", {})
        assert res.available is False
        assert "unknown" in res.reason

    def test_placeholder_version_unavailable(self, tmp_path):
        # No card on disk -> placeholder 0.0.0 -> unavailable.
        res = inference.infer(tmp_path, "detector_yolov8n", {})
        assert res.available is False
        assert "artefact" in res.reason or "trained" in res.reason

    def test_registered_but_missing_artefact_unavailable(self, tmp_path):
        d = tmp_path / "classifier_mobilenetv3"
        d.mkdir()
        (d / "card.json").write_text(json.dumps({
            "name": "classifier_mobilenetv3", "version": "1.0.0",
            "sha256": "x", "size_bytes": 10, "quantisation": "int8",
        }))
        # No .onnx file next to the card.
        res = inference.infer(tmp_path, "classifier_mobilenetv3", {})
        assert res.available is False
        assert "not found" in res.reason

    def test_ort_available_flag_is_bool(self):
        assert isinstance(inference.ort_available(), bool)


class TestRealInference:
    """Build a tiny ONNX model on the fly and run it if onnx/ort present."""

    @staticmethod
    def _make_tiny_onnx(path: Path):
        # A single (3,)->(2,) MatMul model built with onnx helpers if available.
        import onnx
        from onnx import TensorProto, helper

        w = helper.make_tensor("W", TensorProto.FLOAT, [3, 2],
                               [1.0, 0.0, 0.0, 1.0, 0.0, 0.0])
        node = helper.make_node("MatMul", ["input", "W"], ["output"])
        graph = helper.make_graph(
            [node], "tiny",
            [helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 3])],
            [helper.make_tensor_value_info("output", TensorProto.FLOAT, [1, 2])],
            [w],
        )
        model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)])
        onnx.save(model, str(path))

    def test_inference_runs_when_artefact_present(self, tmp_path):
        pytest.importorskip("onnx")
        if not inference.ort_available():
            pytest.skip("onnxruntime not installed")

        d = tmp_path / "fertilizer_lightgbm"
        d.mkdir()
        self._make_tiny_onnx(d / "1.0.0.onnx")
        # Card pointing at v1.0.0.
        (d / "card.json").write_text(json.dumps({
            "name": "fertilizer_lightgbm", "version": "1.0.0",
            "sha256": "", "size_bytes": 1, "quantisation": "fp32",
        }))
        feat = np.array([[6.5, 15.0, 45.0]], dtype=np.float32)
        res = inference.infer(tmp_path, "fertilizer_lightgbm", {"input": feat})
        assert res.available is True
        assert "output" in res.outputs
        # MatMul with identity-ish W picks the first two features.
        got = res.outputs["output"][0]
        assert got[0] == pytest.approx(6.5, abs=1e-3)
        assert got[1] == pytest.approx(15.0, abs=1e-3)


class TestDecodeImage:
    def test_decode_shape_and_range(self):
        from PIL import Image
        import io

        img = Image.new("RGB", (100, 50), (200, 100, 50))
        buf = io.BytesIO()
        img.save(buf, format="PNG")
        tensor = inference.decode_image_bytes(buf.getvalue(), (224, 224))
        assert tensor.shape == (1, 3, 224, 224)
        assert tensor.dtype == np.float32
        assert 0.0 <= tensor.min() and tensor.max() <= 1.0
