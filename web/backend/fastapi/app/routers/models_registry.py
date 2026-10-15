"""Model registry router — OTA manifests + integrity checks."""
from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter

from ..config import get_settings
from ..schemas import ModelCardOut, ModelManifestRequest, ModelManifestResponse
from ..services.models import registry

router = APIRouter(prefix="/internal/models", tags=["models"])

# Model artefacts live under MODELS_DIR (default ./models).
_MODELS_DIR = Path(get_settings().dape_models_dir)


@router.post("/manifest", response_model=ModelManifestResponse)
def manifest(req: ModelManifestRequest) -> ModelManifestResponse:
    reg = registry.Registry.from_dir(_MODELS_DIR)
    man = reg.manifest(client_versions=req.client_versions, base_url=req.base_url)
    return ModelManifestResponse(
        registry_version=man["registry_version"],
        update_count=man["update_count"],
        models=[ModelCardOut(**m) for m in man["models"]],
    )


@router.get("/list", response_model=ModelManifestResponse)
def list_models() -> ModelManifestResponse:
    reg = registry.Registry.from_dir(_MODELS_DIR)
    man = reg.manifest()
    return ModelManifestResponse(
        registry_version=man["registry_version"],
        update_count=man["update_count"],
        models=[ModelCardOut(**m) for m in man["models"]],
    )


@router.get("/runtime")
def runtime_status() -> dict:
    """Report the inference runtime: onnxruntime presence + which models are
    actually loadable (have a non-placeholder artefact on disk)."""
    from ..services.models import inference

    reg = registry.Registry.from_dir(_MODELS_DIR)
    models = []
    for card in reg.all():
        art = _MODELS_DIR / card.name / f"{card.version}.onnx"
        loadable = card.version not in ("", "0.0.0") and art.exists()
        models.append({
            "name": card.name,
            "version": card.version,
            "loadable": loadable,
            "quantisation": card.quantisation,
            "fallback": None if loadable else "deterministic rule engine",
        })
    return {
        "onnxruntime_available": inference.ort_available(),
        "models_dir": str(_MODELS_DIR),
        "models": models,
    }
