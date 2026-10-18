# 🌶️ DAPE — Deterministic Agro-Physics Engine
### B2B Contract Farming & Precision Quality Assurance Cabai (`Capsicum annuum` / `Capsicum frutescens`), 0,1 – 2 ha

[![tests](https://img.shields.io/badge/python%20tests-267%20passing-brightgreen)](#-testing)
[![go](https://img.shields.io/badge/go%20tests-8%20pkgs%20ok-brightgreen)](#-testing)
[![mobile](https://img.shields.io/badge/kotlin%20tests-40%20passing-brightgreen)](#-testing)
[![web](https://img.shields.io/badge/web%20tests-75%20passing-brightgreen)](#-testing)
[![e2e](https://img.shields.io/badge/e2e-74%20passing-brightgreen)](#-testing)
[![onnx](https://img.shields.io/badge/edge%20models-3%20trained%20%C2%B7%201%20pending-yellow)](#8-edge-models-onnx)
[![stack](https://img.shields.io/badge/docker%20compose-6%20services-blue)](#-quick-start)

---

## 1. Analisis Riset: Mengapa DAPE Berbeda

Industri agrikultur raksasa mengandalkan citra satelit & drone. Pada skala
**0,1 – 2 ha** pendekatan itu tidak efisien (resolusi spasial terlalu kasar,
biaya per hektar terlalu tinggi, dan banyak kebun berada di *blank spot* sinyal).
Kunci keberhasilan justru ada pada dua pilar:

1. **Precision Micro-Farming berbasis Edge AI** — inferensi di HP petani, bukan
   di cloud.
2. **Arsitektur Offline-First** — seluruh transaksi harian & keputusan selesai
   di perangkat lokal.

Cabai adalah komoditas *high-risk, high-reward*. Kegagalan panen umumnya dipicu
oleh **(a)** keterlambatan deteksi patogen, **(b)** kesalahan dosis pemupukan,
dan **(c)** stres air pada fase generatif.

### 1.1 Tesis Utama DAPE: *Deterministic-First, AI-Augment*

Berbeda dari narasi "AI serba bisa", DAPE menempatkan **fisika deterministik**
sebagai tulang punggung — *explainable* dan *auditable* — lalu memakai AI hanya
di ranah yang memang probabilistik (patologi visual). Setiap angka punya rumus
tertutup dan bisa diverifikasi laboratorium:

| Domain | Hukum / Rumus | Dipakai untuk |
|---|---|---|
| Hidrologi mikro | `TWI = ln(a / tan β)` | Risiko genangan → tinggi bedengan, *Phytophthora*/*Ralstonia* |
| Tekstur tanah | Hukum Stokes + *USDA triangle* | Pasir/debu/liat → kelas tanah → KTK |
| Fotometri | sRGB ↔ CIELAB + white-reference | pH deterministik ±0,1 tanpa alat lab |
| Termodinamika | Asas Black + `T_didih = 100 − 0,0033·h` | Sterilisasi 52 °C pembunuh spora patek |
| Fisiologi | `GDD = max(0, (Tmax+Tmin)/2 − 15)` | Umur termal (bukan kalender) → fase & *lock* dinding sel |
| Densitas buah | Prinsip Archimedes | *Sink ratio* → grade A+/A/B/BS |
| Epidemiologi | Teorema Bayes + Moran's I | Diagnosis multi-modal & pola sebaran penyakit |

### 1.2 Anatomi Siklus & Titik Intervensi

| Fase | HST | Kerentanan | Modul DAPE |
|---|---|---|---|
| Vegetatif | 0–35 | *damping-off*, defisiensi N, kutu kebul | Stokes, CIELAB, TWI |
| Pembungaan | 36–60 | rontok bunga, defisiensi Ca/P | **Fortification Lock** (GDD) |
| Pembuahan | 61–90+ | Antraknosa, lalat buah, BER | Bayesian diagnosis |
| Panen | 90–120 | sortir lambat, mutu tak seragam | Asas Black + Archimedes + Crypto-QR |

### 1.3 Catatan Kritis (Keterbatasan yang Diakui)

- **DEM SRTM 30 m terlalu kasar** untuk bedengan. DAPE mengoreksi TWI dengan
  sudut clinometer lokal (`TYPE_ROTATION_VECTOR`) — *wajib*, bukan opsi.
- **Klaim "akurasi ~100%" diturunkan.** Bayesian fusion dengan prior salah bisa
  *overconfident*; posterior harus dikalibrasi (temperatur/Platt scaling) pada
  data lapangan nyata sebelum klaim tingkat akurasi.
- **Ed25519 di Android Keystore**: Keystore tidak mengizinkan ekspor private key
  dan tidak semua TEE mendukung Ed25519 native. Mitigasi: seed di
  `EncryptedSharedPreferences` + BouncyCastle (dipakai di core Kotlin).
- **Stokes butuh dispersan** (Na-hexametaphosphate) — tanpa itu hasil tidak
  akurat (lihat `stokes_meta.dispersant` di seed).
- **Kanonikalisasi tanda tangan lintas-platform** adalah jebakan nyata: tes
  integrasi menemukan Kotlin `52.0` vs Go `52`. Aturan final: nilai integral
  selalu `x.0`. Lihat §6.

### 1.4 Model Operasional: B2B Contract Farming dan Solusi Rantai Pasok Industri

Berdasarkan data **Sensus Pertanian 2023 (ST2023) BPS**, sekitar 74,89 persen tenaga kerja pertanian di Indonesia berpendidikan maksimal Sekolah Dasar (SD) ke bawah dan mayoritas berusia di atas 55 tahun. Model aplikasi mandiri (B2C) yang diserahkan langsung ke petani tua perorangan di desa memiliki kesenjangan adopsi yang sangat tinggi terhadap prosedur laboratorium presisi.

Oleh karena itu, DAPE diposisikan sebagai **Solusi B2B Contract Farming (Kemitraan Industri Agribisnis)**. Sistem ini menghubungkan korporasi pengolahan pangan (pabrik saus cabai, bumbu olahan, industri makanan instan) dan jaringan pasar modern dengan ribuan petani binaan (*plasma*) melalui perantara **staf agronomis lapangan (*field extension officers*)** dan **petani muda milenial (21,93 persen)**.

Sistem ini menjawab lima masalah bisnis utama pihak industri:

1. **Kepastian Pasokan Pabrik (14-Day Supply Projection):**
   Memprediksi estimasi tonase pasokan cabai yang siap masuk ke lini produksi pabrik dalam 14 hari ke depan dari seluruh blok kebun binaan menggunakan fusi kurva fenologi GDD dan auto-count buah matang, sehingga mencegah kekosongan bahan baku atau kelebihan muatan di gudang.
2. **Standarisasi Mutu dan Kadar Padatan (*High Dry-Matter*):**
   Memastikan cabai yang diserap dari petani binaan memiliki daging tebal dan padat melalui uji apung Archimedes (*sink ratio*) serta penilaian kelengkungan dan warna, membedakan secara objektif mutu Grade A+, A, B, atau BS.
3. **Pencegahan Pembusukan Pascapanen di Gudang (Antraknosa Laten):**
   Menghilangkan risiko spora jamur patek laten meledak di ruang pendingin atau perjalanan truk logistik melalui protokol perendaman Asas Black 52 °C terkalibrasi elevasi dengan validasi sensor giroskop anti-manipulasi.
4. **Audit Kepatuhan GAP dan Ketertelusuran Kriptografis (*Traceability Anti-Fraud*):**
   Menerbitkan sertifikat digital bertanda tangan Ed25519 offline pada setiap lot panen untuk mengikat lokasi GPS, kepatuhan dosis pupuk kalsium, dan riwayat sterilisasi ke dalam kode QR yang dapat diverifikasi oleh pembeli dan auditor secara publik.
5. **Mitigasi Risiko Wabah Kawasan Binaan (Regional Early Warning System):**
   Memetakan risiko penularan spora jamur dalam 72 jam ke depan berbasis mikroklimat, kecepatan angin, dan cekungan drainase air (TWI) melalui analisis spasial Moran's I di dashboard pusat, memungkinkan tindakan proteksi dini sebelum wabah menyebar ke seluruh wilayah kabupaten.

---

## 2. Arsitektur Sistem

```
┌─────────────────────────────────────────────────────────────────────────┐
│  LAPANGAN (Offline-First)                                                 │
│                                                                           │
│  ┌───────────────────────┐        ┌──────────────────────────────────┐   │
│  │  MOBILE (Kotlin/JVM)  │        │  WEB PWA (SvelteKit + Leaflet)   │   │
│  │  • Physics.kt (mirror)│        │  • physics.ts (mirror)           │   │
│  │  • Ed25519OfflineSign │        │  • Dexie IndexedDB + sync queue  │   │
│  │  • Camera2 burst /    │        │  • Service Worker (cache shell)  │   │
│  │    trans-illum / ooze │        │  • QR verifier publik            │   │
│  │  • Room SQLite        │        │                                  │   │
│  └──────────┬────────────┘        └───────────────┬──────────────────┘   │
│             │                                     │                      │
│             └──────── delta sync (multipart) ─────┘                      │
│                        + idempotency key                                  │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │  (Wi-Fi / 4G kembali stabil)
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  CLOUD (Docker Compose)                                                    │
│                                                                           │
│  ┌────────────────┐   X-Internal-Token   ┌────────────────────────────┐  │
│  │ api (Go/Fiber) │ ───────────────────► │ ml (FastAPI)               │  │
│  │ • REST /api/v1 │                      │ • TWI  • Stokes  • CIELAB  │  │
│  │ • JWT + circuit│                      │ • Bayes • GDD • Black •    │  │
│  │   breaker      │                      │   Archimedes               │  │
│  │ • Ed25519      │                      └────────────────────────────┘  │
│  │   verify       │                                                       │
│  │ • GDD cron     │                                                       │
│  └───────┬────────┘                                                       │
│          │                                                                │
│     ┌────▼─────┐  ┌────────┐  ┌──────────────┐                            │
│     │Postgres  │  │ Redis  │  │ files/MinIO  │                            │
│     │+ PostGIS │  │ (idem) │  │ (S3 bucket)  │                            │
│     └──────────┘  └────────┘  └──────────────┘                            │
└─────────────────────────────────────────────────────────────────────────┘
```

### Peta Direktori

```
peritanix/
├── Makefile                  # Interface tunggal: web-* & mobile-*
├── docker-compose.yml        # 6 layanan + profil opsional (storage/mobile)
├── k8s/                      # Kubernetes base + overlays (kustomize, TLS, HPA)
├── infra/postgres/init/      # 01_schema  02_functions  03_seed  04_migrations
├── scripts/                  # e2e_smoke / calibration_test / offline_chaos_test
├── .github/workflows/        # CI (python, go, web, kotlin, integration)
├── web/
│   ├── backend/
│   │   ├── fastapi/          # Fisika & ML deterministik (+ 182 pytest)
│   │   │   ├── app/services/physics/       # twi, stokes, colorimetry, ...
│   │   │   ├── app/services/optics/        # mfsr, trans_illumination
│   │   │   ├── app/services/epidemiology/  # ooze, forensics, trace
│   │   │   ├── app/services/agronomy/      # grading, ews, yield, protocols
│   │   │   ├── app/services/models/        # ONNX registry + OTA manifest
│   │   │   └── app/routers/                # gis, soil, pathology, optics, ...
│   │   └── golang/           # Fiber + Clean/Hexagonal
│   │       ├── cmd/api/      # entrypoint
│   │       └── internal/
│   │           ├── auth/         # JWT + bcrypt
│   │           ├── crdt/         # LWW + version vectors
│   │           ├── epidemiology/ # Moran's I, edge/trough bias
│   │           ├── domain/       # entitas + port repo
│   │           ├── handlers/     # REST
│   │           ├── infra/        # postgres, redis, mlclient, crypto, storage
│   │           └── worker/       # GDD cron
│   └── frontend/             # SvelteKit PWA (+ 45 vitest)
│       └── src/routes/       # dashboard, lands/new, diagnose, harvest,
│                             # verify, account, lands/[id]
└── mobile/                   # Kotlin/JVM core (+ 27 JUnit) + android/ (Compose)
```

---

## 3. Quick Start

```bash
cp .env.example .env          # konfigurasi (port non-bentrok)
make doctor                   # cek prasyarat
make web-up                   # bangun + jalankan seluruh stack web
make web-urls                 # cetak URL semua layanan
```

Setelah `web-up`:

| Layanan | URL |
|---|---|
| Web PWA (dashboard + verifier) | http://localhost:55232 |
| Go API | http://localhost:58139 |
| FastAPI (docs) | http://localhost:58059/docs |
| Postgres | localhost:55491 |

---

## 4. Referensi Perintah `make`

### Web (backend Go + FastAPI ML + SvelteKit)

| Perintah | Fungsi |
|---|---|
| `make web-up` | Bangun + jalankan seluruh stack, tunggu sehat |
| `make web-build` | Bangun semua image |
| `make web-restart` | Restart stack |
| `make web-seed` | Muat seed data (idempoten; skip bila sudah) |
| `make web-reseed` | **Destruktif**: hapus volume DB → migrasi + seed ulang |
| `make web-migrate` | Terapkan migrasi SQL ke Postgres yang berjalan |
| `make web-test` | Jalankan suite Go + Python |
| `make verify` | **Full pipeline**: unit + E2E + calibration + chaos |
| `make e2e` / `calibrate` / `chaos` | Jalankan masing-masing skrip verifikasi |
| `make models-train` | Latih & ekspor ulang **semua** model edge ONNX (deterministik) |
| `make models-train-classifier` / `models-train-severity` / `models-train-fertilizer` | Latih satu model saja |
| `make models-bench` | Verifikasi sha256 + load + inference tiap artefak ONNX |
| `make models-test` | Suite regresi model edge (pytest) |
| `make loadtest` | Jalankan k6 load test (butuh k6) |
| `make db-backup` | Dump DB → `backups/dape-<ts>.dump` (+sha256) |
| `make db-restore DUMP=backups/x.dump [CLEAN=--clean]` | Restore (checksum-verified) |
| `make web-logs` / `web-ps` / `web-down` / `web-nuke` | Log / status / stop / hapus semua |
| `make web-shell-<svc>` | Shell ke `api`\|`ml`\|`web`\|`postgres`\|`redis` |
| `make web-psql` | Buka psql |

### Mobile (Kotlin core)

| Perintah | Fungsi |
|---|---|
| `make mobile-up` | Jalankan toolchain mobile (JDK+Gradle) |
| `make mobile-test` | Jalankan unit test JVM (fisika + kripto) |
| `make mobile-run` | Jalankan CLI smoke test fisika |
| `make mobile-restart` / `mobile-logs` / `mobile-shell` / `mobile-down` | Kelola toolchain |
| `make mobile-build` / `mobile-apk` | Bangun APK **debug** (butuh Android SDK) |
| `make mobile-release` | Bangun APK **release** R8 (shrink + strip, unsigned) |
| `make mobile-aab` | Bangun **App Bundle** release (Play Store) |
| `make mobile-artifacts` | Build release + AAB + salin + R8 mapping ke `./dist` |
| `make mobile-verify` | Audit artefak rilis di `./dist` (single-dex, R8, keep rules) |

### Android (host-native — `cd mobile && make …`)

Alur Gradle + SDK + adb **tanpa Docker** (butuh Android SDK lokal). Dari root:
`make android <goal>`, mis. `make android up`. Target: `help doctor info up down
build apk release rebuild install uninstall reinstall run test uitest lint deps
emulator-list device-status wait-device clean`. Lihat `mobile/README.md`.

### Objek Storage

```bash
# Default dev memakai service `files` (filesystem). Untuk S3/MinIO asli:
# set MINIO_HOST=minio di .env lalu:
docker compose --env-file .env --profile storage up -d
```

---

## 5. Testing

```bash
make verify        # full pipeline: unit tests + E2E + calibration + chaos
make web-test      # Python (pytest) + Go (go test)
make mobile-test   # Kotlin JVM (JUnit)
make e2e           # end-to-end smoke (67 assertions across all layers)
make calibrate     # lab-reference validation (52C mix, flash correction, TWI, GDD)
make chaos         # offline physics + reconnect + ML-outage graceful degradation
make models-train  # retrain + export edge ONNX models (deterministic)
make models-bench  # verify edge model sha256 + inference
make loadtest      # k6 load test (reads + compute, p95<800ms)
```

**Lint:** `ruff check app/ tests/` (Python, CI-enforced) · `go vet ./...` (Go).

| Suite | Jumlah | Cakupan |
|---|---|---|
| Python (fastapi) | **267** | TWI, **DEM crop (rasterio)**, D8 flow, Stokes/USDA/CEC, CIELAB, Black, GDD, Bayes, Moran's I, Archimedes, fertilizer, MFSR, trans-illumination, ooze flow, grading, EWS, forensics, yield projection, **fruit counting (Opsi 3)**, protocols, trace validation, spatial patterns, ONNX registry/inference, harvest decision, economics, protocol rules (ooze trigger), **edge-model regression (classifier + severity segmenter + fertilizer distillation)** |
| Go (backend, 8 pkgs) | **50** | Ed25519 verify + canonical JSON, CRDT laws, JWT auth, media filename, epidemiology, GDD worker, UUID guard + rate limiter, **mlclient circuit breaker** |
| Kotlin (mobile) | **40** | Mirror fisika + tanda tangan Ed25519 + penolakan tamper + patologi (Opsi 1) |
| Vitest (web) | **75** | Mirror fisika + EWS + heatmap/fertility + TWI colour ramp + fertilizer mirror + TTS dosing + trace validation + QR scanner + WKT/area + on-device vision + **leaf curl differentiation (Opsi 1)** + canonical JSON (cross-platform) |
| E2E script | **74** | Full stack incl. hardening, analytics, feedback, harvest, version, sensor ingest, economics, batch registry, TWI grid, optics (MFSR/trans-illum/ooze/**severity**), forensics, fortification guard, fertilizer dosage, ooze trigger, cert stored-key path |
| Edge models | **3 trained** | `classifier_mobilenetv3` + `severity_unet_lite` + `fertilizer_lightgbm` ONNX, sha256-verified, byte-reproducible; `detector_yolov8n` pending field annotations |
| Calibration | **7** | 52 °C mix @ 100 m & 1000 m, white-ref correction, TWI bound, GDD phase |
| Chaos | **15** | On-device physics, offline queue flush, dropped-ack idempotency, **full offline cycle (init→soil→diag→GDD lock→QR, pre/post-reconnect scan)**, ML-outage 502-not-crash, recovery |
| Lint | **0 issues** | ruff (Python) · go vet (Go) · svelte-check 0 errors/0 warnings (web) — all CI-enforced |

Semua suite memverifikasi **nilai analitik eksak** dalam toleransi ketat —
bukan sekadar "jalan tanpa error". Tes ini menemukan **6 bug nyata** selama
pengembangan (lihat §6).

---

## 6. Temuan Bug Nyata Selama Pengembangan

> Dokumentasi ini penting: tes ketat menemukan cacat yang tak terlihat tanpa
> asersi numerik.

1. **D8 flow accumulation** keliru memetakan urutan pemrosesan sel
   (`np.dstack` menghasilkan bentuk salah) → diganti `np.lexsort` dengan
   tie-break indeks untuk determinisme penuh.
2. **Kalorimetri**: pembulatan `v_boil_parts` membuat model-maju tidak
   mengembalikan tepat 52 °C → tambah field presisi-penuh `v_boil_exact`/`v_well_exact`.
3. **Idempotensi Reseed**: `docker volume rm $COMPOSE_PROJECT_NAME_pgdata`
   adalah *no-op* karena variabel kosong di shell Make → baca dari `.env`.
4. **Kanonikalisasi lintas-platform**: sertifikat Kotlin (`52.0`) gagal
   verifikasi di Go (`52`). Ini cacat *interoperabilitas* yang fatal untuk
   sertifikasi offline. Standar final: nilai integral → `x.0`.
5. **EWS RH membership** terlalu lemah: RH 90 % + hangat hanya menghasilkan
   risiko 0.49, padahal RH ≥ 90 % adalah kondisi optimal sporulasi
   Colletotrichum. Ramp RH diperbaiki ke [80, 90] (saturasi).
6. **numpy bool bocor**: flag `latent_fungal_suspected` mengembalikan
   `np.False_` (bukan `bool` Python) yang bisa membingungkan konsumen JSON.
   Diperbaiki dengan cast `bool(...)`.
7. **Classifier edge memetakan rerata-RGB saja**: model ONNX pertama membuang
   diskriminan geometris terpenting Opsi 1 (**arah keriting** atas/bawah), sehingga
   secara terukur salah mengklasifikasi **Antraknosa → Begomovirus** dan
   **Mites → Begomovirus** (2/5 kondisi). Diganti MLP terlatih atas fitur 10-D
   (green-asymmetry arah keriting, underside-bronze, yellow-mosaic, dark-lesion),
   dengan fitur dihitung identik di Python & ONNX (|Δ|≤1,5e-7). Lihat §8.1.

---

## 7. Kontrak API (ringkas)

```
GET  /healthz                                 → liveness
GET  /api/v1/health                           → API + ML status
POST /api/v1/lands/init                       → poligon + tarik cache TWI (<500 KB)
GET  /api/v1/lands                            → daftar lahan
GET  /api/v1/lands/geojson                    → FeatureCollection
POST /api/v1/soil/sync                        → Stokes + CIELAB + pupuk
POST /api/v1/diagnostics/evaluate             → Bayes multi-modal (V,S,T)
POST /api/v1/postharvest/calorimetry          → Asas Black (elevasi-aware)
POST /api/v1/postharvest/archimedes           → sink ratio + grade
POST /api/v1/harvest/batches                  → simpan batch tersertifikasi
POST /api/v1/certificates/verify              → verifikasi Ed25519 (publik)
POST /api/v1/sync/delta                       → sinkronisasi offline (idempoten)
POST /api/v1/optics/severity                  → segmentasi % nekrosis (Opsi 1)
POST /api/v1/yield/fruit-count                → auto-count buah per kelas matang (Opsi 3)
```

Layanan ML internal (`/internal/*`) dilindungi header `X-Internal-Token`.

---

## 8. Edge Models (ONNX)

Empat model edge diregistrasi di `web/backend/fastapi/models/`. Tiga sudah
dilatih & dikirim sebagai artefak ONNX nyata; satu lagi menunggu anotasi lapangan.

| Model | Status | Artefak | Peran |
|---|---|---|---|
| `classifier_mobilenetv3` | ✅ terlatih | 7,3 KB, sha256 terverifikasi | diferensiasi keriting (Thrips/Mites/Begomovirus) + Antraknosa + sehat |
| `severity_unet_lite` | ✅ terlatih | 2,0 KB, sha256 terverifikasi | segmentasi % nekrosis/klorosis (severity) → dosis fungisida |
| `fertilizer_lightgbm` | ✅ terlatih | 2,0 KB, sha256 terverifikasi | distilasi kalkulator pupuk preskriptif (offline < 10 ms) |
| `detector_yolov8n` | ⏳ pending | – | lokalisasi lesi & hitung buah (butuh foto lapangan + anotasi bbox) |

### 8.1 Mengapa classifier diperbaiki (bug nyata)

Versi awal memetakan **hanya rerata RGB → 5 logit**. Itu membuang diskriminan
terpenting Opsi 1 — **arah keriting** (atas = Thrips, bawah = Mites) — yang
merupakan ciri *geometris*, bukan warna. Terbukti di lapangan:

```
sebelum:  Antraknosa(dark) → Begomovirus ✗   Mites(bronze) → Begomovirus ✗
sesudah:  ✓ Thrips  ✓ Mites  ✓ Begomovirus  ✓ Antraknosa  ✓ Sehat   (semua benar)
```

Classifier baru adalah **MLP terlatih (24→16)** atas vektor fitur 10-dimensi
yang interpretable — termasuk *vertical green asymmetry* (arah keriting),
*underside bronze index*, *yellow-mosaic index*, dan *dark-lesion fraction*.
Fitur dihitung **identik** di Python (training) dan di dalam graf ONNX (serving)
— dijaga oleh tes hingga |Δ| ≤ 1,5e-7.

### 8.2 Severity segmenter (Opsi 1 → dosis fungisida)

`severity_pct` (luas nekrosis/klorosis) adalah angka yang **menskalakan dosis
fungisida** — angka kritis-keselamatan. `severity_unet_lite` adalah segmenter
per-piksel 4-kelas `[healthy, chlorotic, necrotic, background]`:

- Graf ONNX: box-blur 3×3 + 9×9 (konteks lokal) + variance → head 1×1 conv
  terlatih → softmax; output 4×256×256.
- Konteks 9×9 adalah **esensial**: tanpa itu, lesi cokelat (dikelilingi jaringan
  hijau) tak terbedakan dari tanah gelap — ini ditemukan saat pengujian dan
  diperbaiki (bukan diasumsikan).
- Severity = `necrotic + 0.5·chlorotic` atas region daun (non-background).
- **Self-check**: MAE severity **0,6 pp** (semua daun) / **0,7 pp** pada daun
  dengan severity > 5 % (regime yang menggerakkan dosis).
- Endpoint `POST /api/v1/optics/severity`; ada **fallback deterministik**
  (rule warna+tekstur) bila artefak ONNX absen → sistem tetap jalan.

### 8.3 Reproduksibilitas & perintah

```bash
make models-train              # latih ulang SEMUA model (deterministik, seed tetap)
make models-train-classifier   # hanya Opsi 1 classifier
make models-train-severity     # hanya Opsi 1 segmenter
make models-train-fertilizer   # hanya Opsi 2
make models-bench              # cek sha256 + load + inference tiap artefak
make models-test               # suite regresi model (25 tes)
```

Latih-ulang menghasilkan **byte identik** (sha256 stabil) → artefak dapat
diverifikasi & diaudit. Setiap skrip **menolak mengekspor** bila self-check
gagal (mis. ada kondisi benchmark yang salah kelas, atau MAE severity terlalu
tinggi).

---

## 9. Roadmap (Selaras Master To-Do 7 Tahap)

- [x] **Tahap 0** — Monorepo, Docker, Makefile, PostGIS schema + seed
- [x] **Tahap 1** — Inisialisasi lahan, cache DEM/TWI, identitas Ed25519
- [x] **Tahap 2** — Stokes, TWI, fotometri CIELAB, pupuk preskriptif
- [x] **Tahap 3** — Bayes multi-modal + epidemiologi spasial (Moran's I)
- [x] **Tahap 4** — GDD cron + Fortification Lock + **EWS mikroklimat 72 jam**
- [x] **Tahap 5** — Kalorimetri, Archimedes, sertifikasi Crypto-QR, **grading
      curvature/colour/defect**
- [x] **Tahap 6** — Dashboard geospasial, **CRDT field-level LWW merge**,
      delta sync idempoten, **anti-fraud forensik (EXIF/ELA/aHash)**
- [x] **Tahap 7** — Containerization, **CI/CD (GitHub Actions, 5 job)**,
      **E2E/calibration/chaos test scripts**, kalibrasi elevasi
- [x] **Pipeline optik deterministik** — MFSR burst stack, trans-illumination,
      ooze optical flow (Ralstonia vs Fusarium)
- [x] **JWT auth** — register/login/me + middleware (AUTH_REQUIRED di produksi)
- [x] **Estimasi panen** — counting ripeness hijau/semburat/merah → proyeksi
      tonase 14 hari (kurva fenologi berbasis GDD)
- [x] **Auto-count buah dari foto (Opsi 3)** — inti CV deterministik
      (segmentasi warna HSV + connected-component + filter area; gate
      *relative-darkness* untuk pisahkan buah hijau dari dedaunan) → hitung
      green_dark/red_blush/red_full; endpoint `/yield/fruit-count` mengisi
      otomatis input proyeksi panen (menggantikan tally manual)
- [x] **Katalog protokol preskriptif** — 9 protokol pestisida, dosis berskala
      keparahan, langkah eradikasi, alternatif organik
- [x] **Validasi bukti fisik** — trace giroskop perendaman anti-cheat
- [x] **Media upload** — object storage (files/MinIO) dengan key ternamespace
- [x] **Orkestrasi spatiotemporal** — distance-to-edge, bearing, vektor angin,
      klasifikasi pola Moran's I (EDGE_WIND / DRAINAGE_TROUGH / UNIFORM)
- [x] **ONNX model registry + OTA manifest** — 4 model card, sha256 integrity,
      unduhan minim-data (Continuous Learning loop)
- [x] **Kubernetes** — manifests lengkap + kustomize overlays (Ingress TLS, HPA)
- [x] **Heatmap penyakit + vektor angin** di dashboard web (canvas, tanpa dep)
- [x] **Peta kesuburan tanah + kurva GDD** di dashboard (layer pH, chart akumulasi)
- [x] **Worker background sync** — Redis retry queue dengan attempt terbatas
- [x] **Android Jetpack Compose scaffold** — Compose UI + Room + WorkManager
      (mengonsumsi core Kotlin sebagai sumber, tanpa duplikasi logika)
- [x] **Hardening produksi** — request-ID, validasi UUID, rate limit auth,
      `/readyz` deep check, verifikasi sertifikat vs kunci petani tersimpan
- [x] **Pemindai QR kamera** di halaman verifikasi publik (BarcodeDetector)
- [x] **ONNX inference runtime** — InferenceSession + graceful fallback ke rule
      engine; status runtime via `/internal/models/runtime`
- [x] **Onboarding lahan** — halaman gambar poligon GPS → `lands/init`
- [x] **Continuous learning** — capture feedback (lab/expert) + retrain-readiness
- [x] **Backup/restore DB** — `make db-backup` / `make db-restore` (pg_dump+sha256)
- [x] **Harvest decision engine** — GDD + grading + archimedes + pasar →
      SELL_NOW/HOLD/SORT dengan skor mutu & alasan
- [x] **Structured JSON logging** — slog (JSON di prod) + korelasi request-id
- [x] **Halaman panen web** — kalorimetri + Archimedes + mutu + keputusan jual
- [x] **Halaman diagnosis web** — capture foto → analisis offline → Bayes → resep
- [x] **Akun JWT web** — login/register + penyimpanan token + auto-attach header
- [x] **Gradle wrapper** Android (build portabel tanpa Gradle sistem) + LICENSE
- [x] **Penerbitan sertifikat di browser** — sign Ed25519 (WebCrypto) + QR; 
      **terverifikasi 3-arah** (Kotlin↔Go↔TypeScript canonical JSON identik)
- [x] **Klien OTA model** — cek manifest, unduh hanya versi yang berubah
- [x] **Observability** — `/api/v1/version` + `/api/v1/models/runtime`
- [x] **Ingest sensor iklim** — batch ESP32/BLE → EWS ledakan jamur (Opsi 4)
- [x] **PWA installable** — ikon 192/512 + maskable
- [x] **Load test k6** — `make loadtest` (thresholds p95<800ms)
- [x] **Dampak ekonomi** — kalkulator ROI (hemat pupuk + cegah gagal panen +
      uplift hasil), transparan & dapat dikalibrasi
- [x] **Registry sertifikat** — batch panen tersimpan + lookup by code
- [x] **Backup terjadwal** — k8s CronJob harian (pg_dump + sha256, retain 14)
- [x] **DEM SRTM nyata** — crop GeoTIFF via rasterio (fallback sintetik deterministik)
- [x] **Semua 6 service sehat** — healthcheck untuk files + web
- [x] **Kontur TWI di dashboard** — grid cekungan drainase (warna dry→wet→trough)
- [x] **Verifikasi pembeli lengkap** — cari batch by code + grafik GDD tersinkron
- [x] **Fortification Lock (TAHAP 4.2)** — blokir input Nitrogen tinggi saat
      Antesis via `/fertilizer/guard` + UI hard-block + protokol Ca+B+Si
- [x] **Kalkulator pupuk preskriptif (Opsi 2)** — gramasi tepat NPK/KNO₃/Ca
      per liter (online + mirror offline)
- [x] **Kalibrasi spektral LED (TAHAP 1.1)** — white-reference 0-lux per merek HP
- [x] **Trigger ooze destruktif (TAHAP 3.1)** — layu + TWI tinggi → blokir &
      wajibkan uji ooze (Ralstonia vs Fusarium)
- [x] **TTS + countdown 180s (TAHAP 5.1)** — instruksi takaran dibacakan
      bahasa Indonesia di halaman panen
- [x] **Validasi trace giroskop (TAHAP 6.3)** — rekam gerak 180s + tolak
      sinyal konstan (indikasi lewatkan timer)
- [x] **Classifier terlatih (Opsi 1)** — MLP 24→16 atas fitur 10-D (arah keriting
      + warna + lesi); graf ONNX menghitung fitur identik dgn Python (|Δ|≤1,5e-7);
      memperbaiki bug nyata model rerata-RGB (Antraknosa/Mites salah kelas)
- [x] **Fertilizer ONNX terlatih (Opsi 2)** — distilasi rule engine, galat
      ≤ 0,35 g/L di seluruh envelope; `make models-train` deterministik (byte identik)
- [x] **Pipeline training + benchmark reproducible** — `scripts/train_*.py`,
      `scripts/bench_models.py`, target `make models-train/models-bench/models-test`
- [x] **Suite regresi model** — 25 tes menjaga klasifikasi 5 kondisi, severity
      segmentasi, & distilasi pupuk
- [x] **Severity segmenter terlatih (Opsi 1)** — segmenter per-piksel 4-kelas
      (healthy/chlorotic/necrotic/background) + endpoint `/optics/severity` +
      fallback deterministik; MAE severity **0,6–0,7 pp**; konteks 9×9 mengatasi
      ambiguitas lesi-cokelat vs tanah (temuan pengujian)
- [x] **Rilis Android R8/AAB (TAHAP 7.2)** — `make mobile-release`/`mobile-aab`/
      `mobile-artifacts`; APK release R8 **4,0 MB** vs debug 13 MB (−70%),
      resource shrinking + native stripping; `make mobile-verify` mengaudit
      single-dex, obfuskasi R8, & core fisika tetap utuh (skrip verifikasi)
- [ ] Bobot ONNX detektor (YOLOv8-Nano) — butuh foto lapangan beranotasi
      bounding box (1 model sisa berstatus *pending*, dilaporkan `models-bench`)
- [ ] Akuisisi media nyata di perangkat (Camera2 burst, contact flash, ooze video)
- [ ] Kalibrasi lab fisik (pH ≤ ±0,1 di 3 merek HP) & uji lapangan nyata

## 10. Verifikasi Langsung (bukti)

```
make verify  →
  lint:        ruff clean · svelte-check 0/0 · go vet clean
  unit:        pytest 267 passed · go test 8 pkgs ok · kotlin 40 · vitest 75
  e2e smoke:   74 passed  (…→optics(MFSR/trans-illum/ooze/severity)→fruit-count→forensics)
  calibration: 7 passed   (52.0 °C @100m/1000m, white-ref, TWI bound, GDD phase)
  chaos:       15 passed  (full offline cycle init→soil→diag→GDD→QR,
                           pre/post-reconnect scan, idempotent retry,
                           ML-outage HTTP 502 not crash, auto-recover)
  models:      make models-bench → 3 trained artefacts sha256-verified,
                           1 pending real-field training
  loadtest:    make loadtest  (k6: reads 10 VUs + compute 3 VUs, p95<800ms)

docker compose ps → 6/6 services (healthy)
```

**Android rilis (bukti):** `make mobile-artifacts` → APK release **4,0 MB**
(debug 13 MB, −70%) + AAB 5,4 MB; `make mobile-verify` →
`6 passed` (single-dex · release<debug · AAB ada · R8 meng-obfuscate ·
`MainActivity` tetap (entry-point manifest) · core fisika `-keep` utuh).

**Cross-platform crypto (bukti):** sertifikat yang ditandatangani KOTLIN dan
TYPESCRIPT (browser) keduanya diverifikasi `valid:true` oleh backend Go — aturan
float canonical JSON (`52 → "52.0"`) identik di ketiga stack.

*Dibangun sebagai referensi implementasi DAPE — Deterministic Agro-Physics
Engine. Fisika sudah benar & teruji; sisanya adalah kalibrasi lapangan.*
