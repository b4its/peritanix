# DAPE Web — SvelteKit PWA

Management dashboard + public QR certificate verifier. Offline-first: the app
shell is cached by a Service Worker, records live in IndexedDB (Dexie), and the
deterministic physics mirrors run in the browser when there is no connectivity.

## Run

```bash
make web-up
# → http://localhost:55232
```

## Layout

```
src/
├── lib/
│   ├── api.ts        # typed Go-backend client
│   ├── db.ts         # Dexie IndexedDB store + sync queue (idempotency, LWW)
│   └── physics.ts    # browser mirror of the deterministic engine
├── routes/
│   ├── +page.svelte          # control center (status, sync, land list)
│   ├── dashboard/+page.svelte # Leaflet geospatial map
│   ├── lands/[id]/+page.svelte# GDD, 52 °C calculator (offline fallback), soil
│   └── verify/+page.svelte    # public Ed25519 certificate verifier
├── service-worker.ts # precache shell; cache-first assets; API never cached
└── app.html          # PWA manifest + theme
```

## Test

```bash
make web-test                 # runs pytest + go test (backend)
# frontend physics mirror:
docker compose exec web npm test
```
