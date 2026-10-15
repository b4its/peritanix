# DAPE Core Backend (Go / Fiber)

Orchestration layer: REST API, geospatial persistence, offline-sync
idempotency, and cryptographic certificate verification. Talks to the FastAPI
physics/ML engine over the internal network.

## Architecture (Clean / Hexagonal)

```
cmd/api/                  # entrypoint (server, wiring, graceful shutdown)
internal/
├── config/               # env-driven configuration
├── domain/               # entities + repository ports (no infra imports)
├── handlers/             # HTTP routes (Fiber) -> domain ports
├── infra/
│   ├── postgres/         # pgx + PostGIS repositories
│   ├── redis/            # idempotency keys
│   ├── mlclient/         # typed FastAPI client + circuit breaker
│   └── crypto/           # Ed25519 certificate verification + canonical JSON
└── worker/               # GDD cron (daily 00:00 + boot)
```

## Key behaviours

- **Idempotent sync** — `POST /api/v1/sync/delta` uses Redis `SET NX` per
  `idempotency_key`, so retried offline batches never duplicate rows.
- **Circuit breaker** on the ML client: 5 failures opens the circuit for 15 s.
- **Ed25519 verification** — canonical JSON (sorted keys, stable float format)
  shared byte-for-byte with the Kotlin signer and the TS encoder.

## Test

```bash
make web-test
# or directly:
docker compose exec api go test ./...
```

## Run

```bash
make web-up
make web-logs
make web-shell-api
```

See `docs/API.md` for the full contract.
