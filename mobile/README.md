# DAPE Mobile — Kotlin Core & Android App

Device-side deterministic agro-physics engine: the same physics as the backend, plus offline
Ed25519 certificate signing, native C++/JNI bridges, and full Jetpack Compose mobile UI.
This module provides both a **pure Kotlin/JVM core** and a **production-ready Android app**
with offline-first Room SQLite storage and background delta synchronization via WorkManager.

## Run & Test

All operations are containerized via Docker and orchestrated through the root Makefile:

```bash
make mobile-up      # Start the JDK + Android toolchain container
make mobile-test    # 53 unit tests (40 pure JVM + 13 Android unit tests)
make mobile-run     # Physics smoke CLI (prints calorimetry, TWI, GDD, cert)
make mobile-build   # Assemble Android debug APK via Docker (requires mounted Android SDK)
make mobile-apk     # Copy built APK to ./dist/app-debug.apk (~13 MB)
make mobile-shell   # Interactive shell into mobile container
make mobile-down    # Stop and remove mobile container
```

## Android Workflow (native host — `mobile/Makefile`)

When you have the Android SDK + adb + (optionally) an emulator installed locally,
use the host-side Makefile. It builds, installs and runs the app directly on a
device/emulator without Docker:

```bash
cd mobile && make help      # list every target
cd mobile && make doctor    # check java / SDK / adb / emulator / AVD
cd mobile && make info      # app + toolchain + git + debug-keystore SHA-1/SHA-256
cd mobile && make up        # build + install + launch (starts an AVD if none)
```

Or delegate from the repo root: `make android <goal>` (e.g. `make android up`).

| Target | Fungsi |
|---|---|
| `help` | Tampilkan semua target |
| `doctor` | Cek toolchain (java, sdk, adb, emulator, avd) |
| `info` | Info detail proyek & aplikasi (versi, SDK, git, SHA-1/SHA-256 signing) |
| `up` | Build + install + launch di device aktif; nyalakan emulator bila tidak ada device |
| `down` | Matikan emulator yang berjalan |
| `build` / `apk` | Build APK debug |
| `release` | Build APK release |
| `rebuild` | Clean lalu build ulang |
| `install` | Install APK debug ke device aktif |
| `uninstall` | Hapus aplikasi dari device aktif |
| `reinstall` | Uninstall lalu install ulang (atasi konflik versi/signature) |
| `run` | Install + launch aplikasi |
| `test` | Unit test (host JVM) |
| `uitest` | Instrumented test (butuh device/emulator) |
| `lint` | Android lint |
| `deps` | Lihat dependency runtime debug |
| `emulator-list` | Tampilkan daftar AVD |
| `device-status` | Tampilkan device/emulator terhubung beserta detail (model, Android, tipe) |
| `wait-device` | Tunggu device/emulator terhubung |
| `clean` | Hapus hasil build |

**Docker ↔ host interop:** the containerized `make mobile-*` targets run Gradle
as root, which leaves a root-owned `android/.gradle` and `android/app/build`.
To stop that blocking a host build, `mobile/Makefile` keeps its Gradle project
cache in `mobile/.host-cache` (gitignored) and `make clean` wipes it. If a
root-owned cache still exists, clear it once via the container:
`docker exec peritanix-mobile sh -c "rm -rf /app/android/.gradle /app/android/app/build"`.

## Architecture & Layout

```
mobile/
├── src/main/kotlin/id/peritanix/dape/
│   ├── physics/Physics.kt               # TWI, Black's law, GDD, Archimedes, Stokes, Soil Texture
│   ├── pathology/PathologyClassifier.kt # Curl symptom differentiation (Thrips vs Mites)
│   ├── crypto/Ed25519OfflineSigner.kt   # Offline Ed25519 signing + CanonicalJson
│   └── cli/Main.kt                      # Smoke CLI runner
├── src/test/kotlin/id/peritanix/dape/   # Pure-Kotlin JVM unit tests (40 tests)
│   ├── PhysicsTest.kt                   # 22 tests
│   ├── PathologyTest.kt                 # 13 tests
│   └── CryptoTest.kt                    # 5 tests
└── android/                             # Android application module
    ├── app/src/main/java/id/peritanix/dape/android/
    │   ├── ui/
    │   │   ├── MainActivity.kt          # Compose UI entrypoint & navigation tabs
    │   │   ├── LahanScreen.kt           # Clinometer, bed elevation, & soil Stokes sedimentation
    │   │   ├── DiagnosticsScreen.kt     # Burst aligner, trans-illumination, & ooze test
    │   │   ├── HarvestScreen.kt         # Archimedes grading, Asas Black calorimetry, QR cert
    │   │   └── SyncScreen.kt            # CRDT sync queue inspection & force sync trigger
    │   ├── core/
    │   │   ├── hardware/                # Camera2 burst controller, dark chamber, clinometer
    │   │   ├── physics/                 # Local TWI, Stokes, Black calorimetry, sediment bridges
    │   │   └── ai_edge/                 # Edge inference runner (TFLite / ONNX proxy)
    │   └── data/
    │       ├── DapeDatabase.kt          # Room SQLite entities & DAOs (sync_queue, lands, diagnoses)
    │       ├── SyncQueueStore.kt        # Offline-first queue manager
    │       └── SyncWorker.kt            # WorkManager HTTP delta sync to Go Fiber API
    ├── app/src/main/cpp/                # Native OpenCV C++ / NDK implementations
    │   ├── CMakeLists.txt
    │   ├── burst_aligner.cpp            # ORB feature matching & homography warp
    │   ├── sediment_edge_detector.cpp   # Canny edge & Hough line boundary detection
    │   └── archimedes_float_counter.cpp # HSV color mask & contour centroid counting
    └── app/src/test/java/               # Android unit tests (13 tests)
        └── id/peritanix/dape/android/
            ├── AndroidPhysicsCoreTest.kt
            ├── AndroidNativeBridgesTest.kt
            ├── AndroidEdgeInferenceTest.kt
            └── AndroidHardwareTest.kt
```

## Cross-Platform Determinism Guarantee

A certificate signed on the mobile device verifies deterministically on the Go backend
(`POST /api/v1/certificates/verify`). This requires the canonical JSON float
formatting to remain strictly byte-identical across platforms: integral values serialize as `x.0`,
non-integral serialize with shortest round-trip representation (RFC 8785 subset).
`proguard-rules.pro` explicitly keeps `id.peritanix.dape.physics.**` and `.crypto.**`
unobfuscated to preserve canonical serialization in release builds.
