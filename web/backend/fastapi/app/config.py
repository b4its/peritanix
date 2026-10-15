"""DAPE — Application settings (env-driven)."""
from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    dape_env: str = "development"
    internal_token: str = "internal-shared-secret-change-me"

    # Object storage
    minio_endpoint: str = "minio:9000"
    minio_access_key: str = "dape_minio"
    minio_secret_key: str = "dape_minio_secret"
    minio_bucket_raw: str = "dape-raw"
    minio_secure: bool = False

    # Physics constants
    dape_tbase_c: float = 15.0           # chilli base temperature
    dape_target_calorimetry_c: float = 52.0  # patek sterilization target

    # Chilli GDD phase thresholds (cumulative GDD)
    gdd_flowering_start: float = 350.0
    gdd_fruiting_start: float = 700.0
    gdd_harvest_start: float = 1050.0

    # Model registry directory (ONNX artefacts + card.json per model).
    dape_models_dir: str = "models"


@lru_cache
def get_settings() -> Settings:
    return Settings()
