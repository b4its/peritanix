"""Model registry router — OTA manifests + integrity checks."""
from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, HTTPException

from ..config import get_settings
from ..schemas import ModelManifestRequest, ModelManifestResponse, ModelCardOut
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
