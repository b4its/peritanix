# DAPE Web

- `backend/golang/` — Core backend (Fiber, Clean/Hexagonal): REST, PostGIS,
  Redis idempotency, Ed25519 verification, GDD cron.
- `backend/fastapi/` — Computational physics & ML engine (deterministic).
- `frontend/` — SvelteKit PWA dashboard + public QR certificate verifier.

See the root [README](../../README.md) and [docs/](../../docs) for the
architecture, physics reference, and API contract.

```bash
make web-up     # from the repo root
```
