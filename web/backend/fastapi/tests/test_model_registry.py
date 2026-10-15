"""Strict tests: model registry, OTA manifest, integrity."""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.services.models import registry as reg_mod


def _make_artefact(path: Path, content: bytes) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return path


class TestRegistryLoad:
    def test_empty_dir_still_lists_known_models(self, tmp_path):
        reg = reg_mod.Registry.from_dir(tmp_path)
        names = {c.name for c in reg.all()}
        assert "detector_yolov8n" in names
        assert "fertilizer_lightgbm" in names
        # Placeholder cards have version 0.0.0 and no sha.
        assert reg.get("detector_yolov8n").version == "0.0.0"

    def test_loads_card_from_disk(self, tmp_path):
        d = tmp_path / "detector_yolov8n"
        d.mkdir()
        (d / "card.json").write_text(json.dumps({
            "name": "detector_yolov8n", "version": "1.2.3",
            "sha256": "abc", "size_bytes": 100, "quantisation": "int8",
        }))
        reg = reg_mod.Registry.from_dir(tmp_path)
        assert reg.get("detector_yolov8n").version == "1.2.3"

    def test_corrupt_card_ignored(self, tmp_path):
        d = tmp_path / "detector_yolov8n"
        d.mkdir()
        (d / "card.json").write_text("{not json")
        reg = reg_mod.Registry.from_dir(tmp_path)
        # Falls back to placeholder, does not crash.
        assert reg.get("detector_yolov8n").version == "0.0.0"


class TestManifest:
    def test_manifest_flags_changed_versions(self, tmp_path):
        d = tmp_path / "classifier_mobilenetv3"
        d.mkdir()
        (d / "card.json").write_text(json.dumps({
            "name": "classifier_mobilenetv3", "version": "2.0.0",
            "sha256": "x", "size_bytes": 10, "quantisation": "int8",
        }))
        reg = reg_mod.Registry.from_dir(tmp_path)
        # Client has the old version -> update available for it.
        man = reg.manifest(client_versions={"classifier_mobilenetv3": "1.0.0"})
        entry = next(m for m in man["models"] if m["name"] == "classifier_mobilenetv3")
        assert entry["update_available"] is True
        assert man["update_count"] >= 1

    def test_up_to_date_client_no_update(self, tmp_path):
        d = tmp_path / "severity_unet_lite"
        d.mkdir()
        (d / "card.json").write_text(json.dumps({
            "name": "severity_unet_lite", "version": "3.1.0",
            "sha256": "y", "size_bytes": 5, "quantisation": "fp16",
        }))
        reg = reg_mod.Registry.from_dir(tmp_path)
        man = reg.manifest(client_versions={"severity_unet_lite": "3.1.0"})
        entry = next(m for m in man["models"] if m["name"] == "severity_unet_lite")
        assert entry["update_available"] is False

    def test_manifest_includes_url_when_base_url(self, tmp_path):
        d = tmp_path / "detector_yolov8n"
        d.mkdir()
        (d / "card.json").write_text(json.dumps({
            "name": "detector_yolov8n", "version": "1.0.0",
            "sha256": "z", "size_bytes": 1, "quantisation": "int8",
        }))
        reg = reg_mod.Registry.from_dir(tmp_path)
        man = reg.manifest(base_url="https://cdn.example.com/models")
        entry = next(m for m in man["models"] if m["name"] == "detector_yolov8n")
        assert entry["url"] == "https://cdn.example.com/models/detector_yolov8n/1.0.0.onnx"

    def test_registry_version_deterministic(self, tmp_path):
        reg = reg_mod.Registry.from_dir(tmp_path)
        v1 = reg.manifest()["registry_version"]
        v2 = reg.manifest()["registry_version"]
        assert v1 == v2


class TestRegisterAndIntegrity:
    def test_register_computes_sha(self, tmp_path):
        art = _make_artefact(tmp_path / "src" / "model.onnx", b"fake-onnx-weights")
        card = reg_mod.register_model(tmp_path / "reg", "detector_yolov8n",
                                      "1.0.0", art, quantisation="int8")
        assert len(card.sha256) == 64
        assert card.size_bytes == len(b"fake-onnx-weights")
        # card.json written
        assert (tmp_path / "reg" / "detector_yolov8n" / "card.json").exists()

    def test_register_unknown_model_raises(self, tmp_path):
        art = _make_artefact(tmp_path / "m.onnx", b"x")
        with pytest.raises(ValueError):
            reg_mod.register_model(tmp_path, "not_a_model", "1.0.0", art)

    def test_register_missing_file_raises(self, tmp_path):
        with pytest.raises(FileNotFoundError):
            reg_mod.register_model(tmp_path, "detector_yolov8n", "1.0.0",
                                   tmp_path / "nope.onnx")

    def test_integrity_check(self, tmp_path):
        art = _make_artefact(tmp_path / "m.onnx", b"weights-v1")
        card = reg_mod.register_model(tmp_path / "reg", "detector_yolov8n",
                                      "1.0.0", art)
        assert reg_mod.check_integrity(card, art) is True
        # Tamper -> integrity fails.
        art.write_bytes(b"weights-v2")
        assert reg_mod.check_integrity(card, art) is False

    def test_roundtrip_register_then_load(self, tmp_path):
        art = _make_artefact(tmp_path / "m.onnx", b"roundtrip")
        reg_root = tmp_path / "reg"
        reg_mod.register_model(reg_root, "classifier_mobilenetv3", "5.0.0", art)
        reg = reg_mod.Registry.from_dir(reg_root)
        assert reg.get("classifier_mobilenetv3").version == "5.0.0"
