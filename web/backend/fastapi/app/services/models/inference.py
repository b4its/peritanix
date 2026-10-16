"""ONNX inference runtime — serves registered edge models with a safe fallback.

The DAPE edge models (detector, classifier, severity, fertilizer) are exported
to ONNX and registered via services/models/registry. This module loads an
artefact and runs inference through onnxruntime.

Key design: **graceful degradation**. If a model artefact is absent (the common
state before field training) or onnxruntime is unavailable, `infer()` returns
`available: False` and the caller uses the deterministic rule engine instead.
So the system is fully functional with zero learned weights, and gains them the
moment an `.onnx` file is dropped into models/<name>/<version>.onnx.
"""
from __future__ import annotations

import io
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

try:  # onnxruntime is optional at import time.
    import onnxruntime as ort  # type: ignore

    _ORT_AVAILABLE = True
except Exception:  # pragma: no cover - exercised only when ORT missing
    ort = None  # type: ignore
    _ORT_AVAILABLE = False

from . import registry


@dataclass
class InferResult:
    available: bool
    model: str
    version: str = ""
    outputs: dict = field(default_factory=dict)
    reason: str = ""


def ort_available() -> bool:
    return _ORT_AVAILABLE


class _SessionCache:
    """Caches InferenceSession objects keyed by artefact path."""

    def __init__(self) -> None:
        self._sessions: dict[str, ort.InferenceSession] = {}

    def get(self, path: Path) -> ort.InferenceSession:
        key = str(path)
        if key not in self._sessions:
            self._sessions[key] = ort.InferenceSession(
                key, providers=["CPUExecutionProvider"])
        return self._sessions[key]


_cache = _SessionCache()


def _artefact_path(models_dir: Path, name: str, version: str) -> Path:
    return models_dir / name / f"{version}.onnx"


def infer(models_dir: str | Path, name: str,
          inputs: dict[str, np.ndarray],
          client_versions: dict[str, str] | None = None,
          raw: bool = False) -> InferResult:
    """Run inference for a registered model.

    Args:
        models_dir: registry root.
        name: model name (must be in registry.KNOWN_MODELS).
        inputs: mapping of input name -> numpy array.
        client_versions: optional override of the model version to run.
        raw: when True, keep full numpy output arrays (for server-side metric
            computation, e.g. per-pixel severity); when False (default) large
            outputs are bounded to a JSON-friendly summary.

    Returns:
        InferResult. `available=False` signals the caller to fall back.
    """
    models_dir = Path(models_dir)
    reg = registry.Registry.from_dir(models_dir)
    card = reg.get(name)
    if card is None:
        return InferResult(available=False, model=name, reason="unknown model")

    version = (client_versions or {}).get(name, card.version)
    if version in ("", "0.0.0"):
        return InferResult(available=False, model=name,
                            reason="no trained artefact registered")

    art = _artefact_path(models_dir, name, version)
    if not art.exists():
        return InferResult(available=False, model=name, version=version,
                            reason=f"artefact not found: {art.name}")

    if not _ORT_AVAILABLE:
        return InferResult(available=False, model=name, version=version,
                            reason="onnxruntime not installed")

    try:
        sess = _cache.get(art)
        # Feed only the inputs the model actually declares, in order.
        feed = {i.name: inputs[i.name] for i in sess.get_inputs() if i.name in inputs}
        outs = sess.run(None, feed)
        out_names = [o.name for o in sess.get_outputs()]
        if raw:
            outputs = {n: o for n, o in zip(out_names, outs, strict=False)}
        else:
            outputs = {n: _to_jsonable(o) for n, o in zip(out_names, outs, strict=False)}
        return InferResult(available=True, model=name, version=version,
                            outputs=outputs)
    except Exception as e:  # pragma: no cover - defensive
        return InferResult(available=False, model=name, version=version,
                            reason=f"inference error: {e}")


def _to_jsonable(arr: np.ndarray) -> object:
    """Convert an output array to a JSON-friendly structure (bounded size)."""
    a = np.asarray(arr)
    if a.size > 4096:
        return {"shape": list(a.shape), "truncated": True,
                "summary": {"min": float(a.min()), "max": float(a.max()),
                            "mean": float(a.mean())}}
    return a.tolist()


def decode_image_bytes(raw: bytes, size: tuple[int, int]) -> np.ndarray:
    """Decode image bytes into a normalized NCHW float32 tensor for a model.

    Uses Pillow only (no torch). Returns shape (1, 3, H, W) scaled to [0,1).
    """
    from PIL import Image

    img = Image.open(io.BytesIO(raw)).convert("RGB").resize(size, Image.BILINEAR)
    arr = np.asarray(img, dtype=np.float32) / 255.0  # HWC
    arr = np.transpose(arr, (2, 0, 1))               # CHW
    return arr[np.newaxis, ...]                      # NCHW
