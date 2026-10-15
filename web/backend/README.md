# DAPE Backend

Two cooperating services:

- [`golang/`](golang/) — Core API & orchestration (Fiber, Clean architecture).
- [`fastapi/`](fastapi/) — Deterministic physics & ML engine (FastAPI).

The Go backend calls the FastAPI service over the internal Docker network using
the `X-Internal-Token` shared secret. See [docs/API.md](../../docs/API.md).
