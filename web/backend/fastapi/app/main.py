"""DAPE — FastAPI application entrypoint.

Computational physics & ML engine. Called by the Go backend over the internal
network. Protected by a shared internal token (header `X-Internal-Token`).
"""
from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from .config import get_settings
from .routers import (
    gdd_physiology,
    gis_twi,
    pathology_bayesian,
    postharvest,
    soil_photometry,
)

settings = get_settings()

app = FastAPI(
    title="DAPE — Computational Physics & ML Engine",
    version="1.0.0",
    description="Deterministic Agro-Physics Engine (DAPE) for chilli micro-farming.",
)


@app.middleware("http")
async def internal_token_guard(request: Request, call_next):
    """Enforce the shared internal token on /internal/* routes.

    /health is always open. In development the token may be empty (disabled).
    """
    path = request.url.path
    if path.startswith("/internal") and settings.internal_token:
        token = request.headers.get("X-Internal-Token")
        if token != settings.internal_token:
            return JSONResponse(status_code=401,
                                content={"detail": "invalid internal token"})
    return await call_next(request)


@app.get("/health", tags=["meta"])
def health() -> dict:
    return {"status": "ok", "service": "dape-ml", "env": settings.dape_env}


@app.get("/", tags=["meta"])
def root() -> dict:
    return {
        "service": "DAPE ML/Physics",
        "version": "1.0.0",
        "physics": [
            "stokes-sedimentation", "cielab-photometry", "twi-hydrology",
            "black-calorimetry", "archimedes-density", "gdd-physiology",
            "bayesian-diagnosis", "fertilizer-prescription",
        ],
    }


app.include_router(gis_twi.router)
app.include_router(soil_photometry.router)
app.include_router(pathology_bayesian.router)
app.include_router(gdd_physiology.router)
app.include_router(postharvest.router)
