# DAPE ML / Physics Engine (FastAPI)

Deterministic computational physics + ML for the DAPE system. Called by the Go
backend over the internal network, never directly by clients.

## Run

Via the root stack:

```bash
make web-up
make web-logs          # or: make web-shell-ml
```

Interactive docs: http://localhost:58000/docs

## Layout

```
app/
├── main.py                 # FastAPI app + internal-token middleware
├── config.py               # env-driven settings
├── schemas.py              # Pydantic request/response models
├── routers/
│   ├── gis_twi.py          # DEM / flow accumulation / TWI grid
│   ├── soil_photometry.py  # Stokes, CIELAB, fertilizer plan
│   ├── pathology_bayesian.py # multi-modal Bayesian fusion
│   ├── gdd_physiology.py   # Growing Degree Days + phases
│   └── postharvest.py      # Black's-law calorimetry + Archimedes
└── services/physics/       # pure deterministic functions (the core)
    ├── twi.py  stokes.py  colorimetry.py  calorimetry.py
    ├── gdd.py  bayesian.py  archimedes.py  fertilizer.py
```

## Test

```bash
make web-test                       # from the repo root
# or directly:
docker compose run --rm --no-deps ml pytest -v
```

79 tests, asserting exact analytic values within tight tolerances. See
`docs/PHYSICS.md` for the formulas.
