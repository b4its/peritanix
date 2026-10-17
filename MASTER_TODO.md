# Master To-Do List: Implementasi End-to-End Deterministic Agro-Physics Engine (DAPE) & Precision Micro-Farming Cabai

Dokumen ini adalah rekapitulasi eksekusi status implementasi produksi tahap demi tahap untuk sistem DAPE (Deterministic Agro-Physics Engine) pada budidaya cabai (*Capsicum annuum* / *Capsicum frutescens*) skala $0,1$ hingga $2\text{ hektar}$.

---

## TAHAP 0: Persiapan Infrastruktur, Blueprint Repositori & Database Inti

Tujuan: Membangun fondasi monorepo, menyiapkan basis data geospasial, dan mengunci kontrak komunikasi antar-layanan.

### 0.1. Setup Struktur Proyek (Directory Blueprint)

- [x] **Inisialisasi Repositori Android (`mobile/android/` & `mobile/`):**
	- [x] Setup project Kotlin (`minSdk = 26`, `targetSdk = 35`, Jetpack Compose BOM) dengan dukungan NDK (C++ / JNI) untuk OpenCV C++ di [`mobile/android/app/build.gradle.kts`](mobile/android/app/build.gradle.kts).
	- [x] Struktur modul: `core/hardware/`, `core/physics/`, `core/crypto/`, `core/ai_edge/`, `core/sync/`, dan `cpp/`.
	- [x] Dependensi lokal: Room SQLite Database (`androidx.room`), WorkManager (`androidx.work`), Camera2 API, SensorManager, TensorFlow Lite / ONNX Mobile runtime proxy, dan BouncyCastle Ed25519 (`org.bouncycastle:bcprov-jdk18on:1.79`).
- [x] **Inisialisasi Repositori Core Backend (`web/backend/go/`):**
	- [x] Setup project Golang Fiber dengan Clean / Hexagonal Architecture: `cmd/api/`, `internal/domain/`, `internal/handlers/`, `internal/crdt/`, `internal/infra/mlclient/`, dan `internal/worker/`.
	- [x] Dependensi: JWT Auth (`golang-jwt/jwt/v5`), database/sql + `lib/pq` dengan PostGIS spatial queries, Redis Client (`redis/go-redis/v9`), Cron Scheduler (`robfig/cron/v3`), Circuit Breaker, dan rate limiter.
- [x] **Inisialisasi Repositori Computational Physics & ML (`web/backend/fastapi/`):**
	- [x] Setup project Python 3.11+ dengan FastAPI, Uvicorn, PyTorch, ONNX Runtime, OpenCV-Python, SciPy, Rasterio, dan NumPy.
	- [x] Direktori: `app/routers/`, `app/services/optics/`, `app/services/epidemiology/`, `app/services/agronomy/`, dan `app/models/`.
- [x] **Inisialisasi Repositori Web & Verifier (`web/frontend/`):**
	- [x] Setup project SvelteKit (TypeScript) + TailwindCSS + Mapbox GL JS / Leaflet + Chart.js.
	- [x] Fitur PWA: Service Worker offline asset cache, IndexedDB (Dexie.js), dan WebAssembly QR scanner verifier.
	- [x] Routing: `src/routes/dashboard/` dan `src/routes/verify/[qr_payload]/`.

### 0.2. Setup Database & Storage (PostgreSQL + PostGIS, Redis, S3/MinIO)

- [x] Instance PostgreSQL 16 dengan ekstensi spasial PostGIS aktif: `CREATE EXTENSION IF NOT EXISTS postgis;`.
- [x] Skema tabel inti di [`01_schema.sql`](infra/postgres/01_schema.sql) & [`04_migrations.sql`](infra/postgres/04_migrations.sql):
	- [x] Tabel `users`: `id` (UUID), `name`, `role`, `ed25519_public_key` (TEXT).
	- [x] Tabel `lands`: `id` (UUID), `user_id`, `boundary` (GEOMETRY Polygon, 4326), `elevation_m` (FLOAT), `twi_raster_url`, `planting_date`, `accumulated_gdd` (FLOAT), `current_phase` (ENUM: VEGETATIVE, FLOWERING, FRUITING, HARVEST, FORTIFICATION_LOCKED).
	- [x] Tabel `soil_assessments`: `id`, `land_id`, `sand_pct`, `silt_pct`, `clay_pct`, `soil_class`, `cec_meq` (FLOAT), `ph_deterministic` (FLOAT), `nitrate_ppm`, `dark_chamber_lux_check` (BOOL).
	- [x] Tabel `disease_logs`: `id`, `land_id`, `point_location` (GEOMETRY Point, 4326), `local_twi` (FLOAT), `mfsr_image_url`, `trans_illum_image_url`, `ooze_video_url`, `posterior_probabilities` (JSONB), `confirmed_disease` (VARCHAR), `severity_pct` (FLOAT).
	- [x] Tabel `harvest_batches`: `id`, `land_id`, `boiling_point_calc` (FLOAT), `hot_water_ratio` (VARCHAR), `archimedes_sink_ratio` (FLOAT), `grade_result` (VARCHAR), `crypto_signature` (TEXT).
- [x] Object Storage MinIO S3 dengan bucket `dape-raw` untuk burst frames YUV, trans-illumination, TWI raster, dan video ooze test.

---

## TAHAP 1: Alur 0 — Inisialisasi Lahan, Caching Geospasial & Identitas Kriptografi

Tujuan: Pemetaan poligon lahan, penyiapan kunci Ed25519 offline, dan caching hidrologi mikro (DEM SRTM 30m).

### 1.1. Mobile App (`dape-mobile-kotlin`)

- [x] **Perekaman Poligon GPS**: UI perekaman batas poligon tertutup lahan di [`LahanScreen.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/ui/LahanScreen.kt).
- [x] **Pembangkitan Kunci Kriptografi**: [`Ed25519OfflineSigner.kt`](mobile/src/main/kotlin/id/peritanix/dape/crypto/Ed25519OfflineSigner.kt) menyimpan Private Key di Android Keystore / software seed dan mengekspor Base64 Public Key ke server.
- [x] **Kalibrasi Spektral LED Flash**: Prosedur kalibrasi mangkuk gelap ($0\text{ Lux}$) menghasilkan White Reference Matrix di [`DarkChamberFlashManager.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/hardware/DarkChamberFlashManager.kt).

### 1.2. Core Backend (`dape-backend-go`) & ML Service (`dape-ml-fastapi`)

- [x] **Endpoint Inisialisasi Lahan**: `POST /api/v1/lands/init` di Golang menyimpan boundary PostGIS (SRID 4326) dan memanggil service ML.
- [x] **Ekstraksi DEM & Flow Accumulation**: `POST /internal/gis/extract-dem` di [`gis_twi.py`](web/backend/fastapi/app/routers/gis_twi.py) memotong DEM SRTM 30m menggunakan Rasterio & SciPy, menghitung *upslope area* ($a$) dan kompresi paket raster TWI $< 500\text{ KB}$ untuk di-cache di Room DB.

---

## TAHAP 2: Modul 1 (Alur 1) — Geoteknik, Hidrologi Mikro (TWI), Hukum Stokes & Fotometri Ruang Gelap

Tujuan: Diagnosis sifat fisik dan kimia tanah secara deterministik ($0 - 35\text{ HST}$) tanpa lab mahal.

### 2.1. Clinometer & Topological Wetness Index (FR-1.3)

- [x] **Clinometer Accelerometer**: [`ClinometerSensor.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/hardware/ClinometerSensor.kt) membaca `TYPE_ROTATION_VECTOR` dengan presisi $0,1^\circ$.
- [x] **Kalkulasi TWI**: [`TwiLocalCompute.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/physics/TwiLocalCompute.kt) menghitung $\text{TWI} = \ln(a / \tan\beta)$.
- [x] **Rekomendasi Bedengan**: Jika $\text{TWI} \ge 7,0$ sistem mewajibkan pembuatan bedengan tinggi $50\text{ cm}$ untuk mencegah patogen tular tanah.

### 2.2. Stokes Sedimentation Analyzer (FR-1.1)

- [x] **Panduan Uji Kocokan**: Pengambilan foto terarah pada $t=40\text{s}$ (Pasir) dan $t=7200\text{s}$ (Debu vs Liat) di [`StokesCalculator.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/physics/StokesCalculator.kt).
- [x] **Water-Level Camera Overlay**: Shutter terkunci kecuali posisi HP tegak lurus ($90^\circ \pm 5^\circ$) terhadap botol.
- [x] **Deteksi Batas Lapisan C++**: [`sediment_edge_detector.cpp`](mobile/android/app/src/main/cpp/sediment_edge_detector.cpp) mendeteksi boundary lapisan sedimentasi secara sub-pixel.
- [x] **Segitiga Tekstur USDA & KTK**: Pemetaan fraksi ke matriks USDA Soil Texture Triangle untuk menghasilkan Kapasitas Tukar Kation (`cec_meq`) di [`soil_photometry.py`](web/backend/fastapi/app/routers/soil_photometry.py).

### 2.3. Controlled Dark-Chamber Photometry & Prescriptive Tabular AI (FR-1.2 & Opsi Topik 2)

- [x] **Mangkuk Gelap Kedap Cahaya**: Verifikasi `TYPE_LIGHT` wajib $\le 0,5\text{ Lux}$ di [`DarkChamberFlashManager.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/hardware/DarkChamberFlashManager.kt).
- [x] **Penguncian Kamera**: Parameter Camera2 dikunci: `CONTROL_AE_MODE_OFF`, ISO 100, Shutter 1/125s, dan Flash Torch.
- [x] **Kalibrasi Warna CIELAB**: Konversi sRGB ke CIELAB ($L^*, a^*, b^*$) untuk pH tanah deterministik ($\pm 0,1$) dan estimasi Nitrat (ppm).
- [x] **Kalkulator Preskriptif Pupuk**: Formulasi stoikiometri pupuk kocor NPK 16-16-16, $\text{KNO}_3$, dan Kalsium/Dolomit per liter air di [`Physics.kt`](mobile/src/main/kotlin/id/peritanix/dape/physics/Physics.kt) dan runtime ONNX.

---

## TAHAP 3: Modul 2 (Alur 2) — Diagnostik Patologi Multi-Modal, Edge AI & Bayesian Spatiotemporal

Tujuan: Mendiagnosis penyakit kritis cabai (Thrips vs Tungau vs Begomovirus, Antraknosa, Ralstonia vs Fusarium) mendekati akurasi 100%.

### 3.1. Akuisisi Bukti Multi-Modal & Edge AI di Lapangan (`dape-mobile-kotlin`)

- [x] **Penguncian Spasial**: Koordinat GPS titik pohon ($S$) dan skor TWI lokal ($T$).
- [x] **12-Frame Burst Super-Resolution (MFSR)**: [`Camera2BurstController.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/hardware/Camera2BurstController.kt) menangkap 12 frame YUV dalam $0,4\text{ detik}$, dengan mitigasi RAM $< 4\text{ GB}$ di [`burst_aligner.cpp`](mobile/android/app/src/main/cpp/burst_aligner.cpp).
- [x] **Active Leaf Trans-Illumination**: Mode kamera kontak ($0\text{ cm}$) mengukur rasio atenuasi optik mesofil daun.
- [x] **Protokol Uji Ooze Destruktif**: Jika tanaman layu pada cekungan TWI tinggi, UI mengunci form dan mewajibkan video celup batang $10\text{ detik}$.
- [x] **Edge AI On-Device**: Model MobileNetV3 / YOLOv8-Nano INT8 di [`PathologyClassifier.kt`](mobile/src/main/kotlin/id/peritanix/dape/pathology/PathologyClassifier.kt) dan [`EdgeInferenceRunner.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/ai_edge/EdgeInferenceRunner.kt) untuk diferensiasi keriting daun (*curl up* = Thrips, *curl down* = Tungau, kuning = Begomovirus).

### 3.2. Orkestrasi & Komputasi Heavy-Compute Bayesian (`dape-backend-go` & `dape-ml-fastapi`)

- [x] **Endpoint Orkestrasi**: `POST /api/v1/diagnostics/evaluate` di Golang mengunggah media ke S3 dan memanggil service ML.
- [x] **4 Pipeline Paralel di FastAPI** ([`pathology_bayesian.py`](web/backend/fastapi/app/routers/pathology_bayesian.py)):
	- [x] Pipeline 1 (MFSR Stacker): Deep burst deblurring dan perhitungan `mfsr_sharpness_gain`.
	- [x] Pipeline 2 (Trans-Illumination CNN): Deteksi hifa laten mesofil $3-4$ hari pra-gejala.
	- [x] Pipeline 3 (Ooze Optical Flow): Dense Optical Flow mendeteksi partikel benang bakteri putih (*Ralstonia* vs *Fusarium*).
	- [x] Pipeline 4 (Spatiotemporal Bayesian): Autokorelasi Moran's I terhadap arah angin dan drainase TWI.
	- [x] Fusi Teorema Bayes:
	  $$P(D_i \mid V, S, T) = \frac{P(V \mid D_i) \cdot P(S \mid D_i) \cdot P(T \mid D_i) \cdot P(D_i)}{\sum_j P(V \mid D_j) \cdot P(S \mid D_j) \cdot P(T \mid D_j) \cdot P(D_j)}$$

---

## TAHAP 4: Modul 3 (Alur 3) — Rekayasa Fisiologi, Siklus GDD & Penguncian Dinding Sel

Tujuan: Intervensi fase pembungaan ($36 - 60\text{ HST}$) dan pembuahan ($61 - 90+\text{ HST}$) berdasarkan umur termal biologis.

### 4.1. GDD & Solar Geometry Scheduler (FR-3.1)

- [x] **Golang Background Cron**: [`gdd_cron.go`](web/backend/go/internal/worker/gdd_cron.go) mengeksekusi perhitungan GDD harian dengan $T_{\text{base}} = 15^\circ\text{C}$:
  $$\text{GDD} = \max\left(0, \frac{T_{\max} + T_{\min}}{2} - 15^\circ\text{C}\right)$$
- [x] **Microclimate Early Warning System (EWS)**: Model peramalan kelembaban udara $> 80\%$ untuk peringatan ledakan spora blast/antraknosa 72 jam ke depan.

### 4.2. Pericarp Fortification Lock (FR-3.2)

- [x] **Trigger Backend**: Saat antesis tercapai, status lahan berubah menjadi `FORTIFICATION_LOCKED`.
- [x] **Intervensi UI & Pemblokiran Pupuk**: UI mengunci form dan memblokir pemupukan Urea / Nitrogen tinggi, serta mewajibkan aplikasi Kalsium Nitrat + Boron + Asam Silikat di [`DiagnosticsScreen.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/ui/DiagnosticsScreen.kt).

---

## TAHAP 5: Modul 4 (Alur 4) — Pasca-Panen Termodinamika, Uji Archimedes & Sertifikasi Crypto-QR

Tujuan: Sterilisasi spora laten tanpa termometer, uji fisik kepadatan, dan sertifikat mutu kriptografis offline ($90 - 120\text{ HST}$).

### 5.1. Volumetric Calorimetry Calculator (FR-4.1)

- [x] **Kalorimetri Asas Black**: [`BlackCalorimetryEngine.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/physics/BlackCalorimetryEngine.kt) mengoreksi titik didih elevasi:
  $$T_{\text{didih}} = 100 - 0,0033h$$
  $$V_{\text{didih}} \cdot (T_{\text{didih}} - 52) = V_{\text{sumur}} \cdot (52 - T_{\text{sumur}})$$
- [x] **Audio TTS & Countdown**: Pemandu suara takaran gayung air mendidih vs sumur dan hitung mundur $180\text{ detik}$.

### 5.2. Archimedes Density Assay & Automated Grading (FR-4.2 & Opsi Topik 5)

- [x] **Uji Apung Larutan Garam**: [`archimedes_float_counter.cpp`](mobile/android/app/src/main/cpp/archimedes_float_counter.cpp) menghitung persentase buah tenggelam (*high dry-matter*) vs melayang.
- [x] **Automated Grading**: Klasifikasi otomatis mutu panen ke dalam Grade A+, A, B, atau BS di [`HarvestScreen.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/ui/HarvestScreen.kt).

### 5.3. Pencetakan Offline Crypto-QR & Verifikasi Pembeli

- [x] **Offline Ed25519 Signer**: Penandatanganan payload kanonikal (RFC 8785) menggunakan private key di Android Keystore.
- [x] **Portal Verifikasi Publik**: Halaman scanner QR publik tanpa login di `src/routes/verify/[qr_payload]/` dan endpoint Go `POST /api/v1/certificates/verify`.

---

## TAHAP 6: Dashboard Web Geospasial, Sinkronisasi Offline-First & Anti-Cheat

Tujuan: Pusat kendali agronomis visual, sinkronisasi tahan gangguan, dan mitigasi manipulasi data.

### 6.1. Dashboard Analitik Agronomis (`dape-web-svelte`)

- [x] Peta 3D TWI & kesuburan tanah dengan Mapbox GL JS / Leaflet layer PostGIS.
- [x] Spatiotemporal Disease Heatmap lengkap dengan indikator arah panah angin vs drainase.
- [x] Grafik Chart.js akumulasi GDD dan estimasi tonase panen 14 hari ke depan.

### 6.2. Mekanisme Sinkronisasi Offline-First & Resolusi Konflik

- [x] Antrean sinkronisasi latar belakang WorkManager di [`SyncWorker.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/data/SyncWorker.kt) mengirimkan delta JSON via HTTP POST `/api/v1/sync/delta`.
- [x] Deduplikasi idempoten di Go + Redis dengan status response `applied` / `duplicate`.
- [x] Algoritma resolusi konflik Last-Write-Wins (LWW) dengan Timestamp Vector di [`crdt.go`](web/backend/go/internal/crdt/crdt.go).

### 6.3. Sistem Audit Keamanan & Anti-Fraud

- [x] Rekam jejak sensor gyroscope selama perendaman 180 detik di [`BlackCalorimetryEngine.kt`](mobile/android/app/src/main/java/id/peritanix/dape/android/core/physics/BlackCalorimetryEngine.kt).
- [x] Penyertaan hash SHA-256 byte citra asli ke dalam payload sertifikat.
- [x] Pemeriksaan metadata EXIF, Error Level Analysis (ELA), dan pHash di FastAPI [`anti_fraud.py`](web/backend/fastapi/app/routers/anti_fraud.py).

---

## TAHAP 7: Pengujian End-to-End (QA), Kalibrasi Lapangan & Deployment Produksi

Tujuan: Validasi akurasi laboratorium, chaos simulation, dan rilis kontainer produksi.

### 7.1. Pengujian Integrasi & Kalibrasi Agronomi

- [x] Validasi Hukum Stokes & pH ruang gelap CIELAB vs lab standar ($\Delta \text{pH} \le \pm 0,1$).
- [x] Verifikasi kalorimetri Asas Black pada elevasi $100\text{ mdpl}$ vs $1000\text{ mdpl}$ mencapai $52^\circ\text{C}$ tepat.
- [x] Simulasi Mode Pesawat (100% Offline): Seluruh alur (lahan, tanah, burst, GDD lock, cetak QR) berjalan offline, dan QR berhasil diverifikasi oleh web verifier sebelum dan sesudah online.

### 7.2. Containerization & CI/CD Deployment

- [x] Multi-stage Dockerfile untuk semua layanan: `postgres`, `redis`, `files` (MinIO), `api` (Go), `ml` (FastAPI), `web` (SvelteKit), dan `mobile` (Android/Kotlin).
- [x] Makefile deklaratif dengan target `web-*` dan `mobile-*` yang lengkap dan terverifikasi.
- [x] Pipeline rilis Android APK: **debug** 13 MB + **release R8** 4,0 MB (−70%) + AAB 5,4 MB, dibangun via Docker (`make mobile-artifacts`, diverifikasi `make mobile-verify`).
- [x] **Edge models ONNX terlatih (3/4)**: `classifier_mobilenetv3` (7,3 KB), `severity_unet_lite` (2,0 KB), `fertilizer_lightgbm` (2,0 KB) — sha256 terverifikasi & byte-reproducible; `detector_yolov8n` menunggu anotasi lapangan.

---

## Rekapitulasi Pengujian Sistem (500+ Tests, 100% Green)

| Test Runner | Modul yang Diuji | Status | Jumlah Uji |
| :--- | :--- | :---: | :---: |
| **make mobile-test (JVM Core)** | Pure-Kotlin Physics & RFC 8785 Ed25519 | **PASS** | 40 tests |
| **make mobile-test (Android Tests)** | GPS Polygon, Water-Level Stokes, EWS, Pod Grading, LightGBM Tabular, U-Net 4-Class, QR Canvas, Thermal Printer | **PASS** | 62 tests |
| **make web-test (Pytest ML)** | DEM TWI, Stokes, Bayesian Pathology, CIELAB, **edge models (classifier + severity + fertilizer)**, **fruit counting (Opsi 3)** | **PASS** | 269 tests |
| **make web-test (Go API)** | Clean Backend, PostGIS, CRDT LWW, JWT Auth | **PASS** | 8 packages |
| **npm test (Web Vitest)** | SvelteKit PWA, Dexie DB, Heatmap, QR Verifier | **PASS** | 75 tests |
| **make e2e (Smoke Integration)** | Integrasi Multi-Layanan End-to-End (incl. severity + fruit-count) | **PASS** | 74 assertions |
| **make models-bench** | 3 artefak ONNX sha256 + inference | **PASS** | 3 models |
| **make verify** | lint + calibration (7) + chaos (15) | **PASS** | 22 checks |
