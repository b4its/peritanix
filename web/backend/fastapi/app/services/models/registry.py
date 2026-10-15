"""Model registry — versioned ONNX artefacts with OTA manifests.

The DAPE edge models (YOLOv8-Nano detector, MobileNetV3 disease classifier,
U-Net severity, LightGBM fertilizer) are trained offline and exported to ONNX,
then quantised (FP16/INT8) for on-device inference. This registry tracks their
versions and serves a *manifest* the mobile/PWA client uses to decide whether to
download a new model when it next has Wi-Fi (Continuous Learning loop).

Design: deterministic and file-system backed. A "model card" describes each
artefact; the registry resolves the current version, checks sha256 integrity,
and produces an OTA manifest with only the models whose version changed since
the client's last-known set.
"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from pathlib import Path

# The canonical DAPE model set (interface stable; weights are swapped in).
KNOWN_MODELS = {
    "detector_yolov8n": {
        "purpose": "locate lesions & count fruit (bites/boxes)",
        "input": "image/uint8 640x640",
        "format": "onnx",
    },
    "classifier_mobilenetv3": {
        "purpose": "disease classification (thrips/mites/begomovirus/anthracnose)",
        "input": "image/uint8 224x224",
        "format": "onnx",
    },
    "severity_unet_lite": {
        "purpose": "necrosis/lesion area segmentation (severity %)",
        "input": "image/uint8 256x256",
        "format": "onnx",
    },
    "fertilizer_lightgbm": {
        "purpose": "prescriptive fertilizer mix (tabular)",
        "input": "float32[N]",
        "format": "onnx",
    },
}


@dataclass
class ModelCard:
    name: str
    version: str
    sha256: str
    size_bytes: int
    quantisation: str  # fp32 | fp16 | int8
    min_app_version: str = "1.0.0"
    purpose: str = ""
    input_spec: str = ""
    format: str = "onnx"

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "version": self.version,
            "sha256": self.sha256,
            "size_bytes": self.size_bytes,
            "size_mb": round(self.size_bytes / (1024 * 1024), 3),
            "quantisation": self.quantisation,
            "min_app_version": self.min_app_version,
            "purpose": self.purpose,
            "input": self.input_spec,
            "format": self.format,
        }


@dataclass
class Registry:
    """A file-system model registry rooted at `root`."""
    root: Path
    cards: dict[str, ModelCard] = field(default_factory=dict)

    @classmethod
    def from_dir(cls, root: str | Path) -> Registry:
        root = Path(root)
        reg = cls(root=root)
        if root.exists():
            for meta in sorted(root.glob("*/card.json")):
                try:
                    data = json.loads(meta.read_text())
                    card = ModelCard(
                        name=data["name"], version=data["version"],
                        sha256=data["sha256"], size_bytes=data["size_bytes"],
                        quantisation=data.get("quantisation", "fp32"),
                        min_app_version=data.get("min_app_version", "1.0.0"),
                        purpose=data.get("purpose", ""),
                        input_spec=data.get("input", ""),
                        format=data.get("format", "onnx"),
                    )
                    reg.cards[card.name] = card
                except Exception:
                    continue
        # Ensure every known model has a card (placeholder if no artefact yet).
        for name, spec in KNOWN_MODELS.items():
            if name not in reg.cards:
                reg.cards[name] = ModelCard(
                    name=name, version="0.0.0", sha256="", size_bytes=0,
                    quantisation="fp32", purpose=spec["purpose"],
                    input_spec=spec["input"], format=spec["format"])
        return reg

    def get(self, name: str) -> ModelCard | None:
        return self.cards.get(name)

    def all(self) -> list[ModelCard]:
        return list(self.cards.values())

    def manifest(self, client_versions: dict[str, str] | None = None,
                 base_url: str = "") -> dict:
        """Build an OTA manifest for a client.

        Only models whose current version differs from the client's are flagged
        as `update_available`, so a farmer on metered data downloads the minimum.
        """
        client_versions = client_versions or {}
        updates: list[dict] = []
        for card in sorted(self.cards.values(), key=lambda c: c.name):
            have = client_versions.get(card.name)
            changed = have != card.version
            entry = card.to_dict()
            entry["update_available"] = changed
            if base_url:
                entry["url"] = f"{base_url.rstrip('/')}/{card.name}/{card.version}.onnx"
            updates.append(entry)
        return {
            "registry_version": self._registry_version(),
            "models": updates,
            "update_count": sum(1 for e in updates if e["update_available"]),
        }

    def _registry_version(self) -> str:
        # Deterministic digest of (name, version) pairs.
        h = hashlib.sha256()
        for c in sorted(self.cards.values(), key=lambda c: c.name):
            h.update(f"{c.name}:{c.version};".encode())
        return h.hexdigest()[:16]


def sha256_file(path: str | Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def register_model(root: str | Path, name: str, version: str,
                   artefact_path: str | Path, quantisation: str = "int8",
                   **extra) -> ModelCard:
    """Register (or overwrite) a model artefact and write its card.json.

    This is the *offline training pipeline's* entry point: it computes the
    sha256, copies metadata next to the artefact, and writes card.json.
    """
    if name not in KNOWN_MODELS:
        raise ValueError(f"unknown model name: {name}")
    root = Path(root)
    artefact_path = Path(artefact_path)
    if not artefact_path.exists():
        raise FileNotFoundError(artefact_path)
    sha = sha256_file(artefact_path)
    size = artefact_path.stat().st_size
    spec = KNOWN_MODELS[name]
    card = {
        "name": name,
        "version": version,
        "sha256": sha,
        "size_bytes": size,
        "quantisation": quantisation,
        "purpose": spec["purpose"],
        "input": spec["input"],
        "format": spec["format"],
        **extra,
    }
    dest = root / name
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "card.json").write_text(json.dumps(card, indent=2))
    return ModelCard(
        name=name, version=version, sha256=sha, size_bytes=size,
        quantisation=quantisation, purpose=spec["purpose"],
        input_spec=spec["input"], format=spec["format"])


def check_integrity(card: ModelCard, artefact_path: str | Path) -> bool:
    """Verify an artefact matches the card's sha256."""
    if not card.sha256:
        return False
    return sha256_file(artefact_path) == card.sha256
