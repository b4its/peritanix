# DAPE Mobile — Kotlin Core

Device-side deterministic engine: the same physics as the backend, plus offline
Ed25519 certificate signing. This module is a **pure Kotlin/JVM core** so it can
be unit-tested without the Android SDK; the Android UI layer (Jetpack Compose,
Camera2, Room, WorkManager, OpenCV NDK) wraps this core on-device.

## Run & Test

```bash
make mobile-up      # start the JDK + Gradle toolchain
make mobile-test    # 27 JUnit tests (physics + crypto)
make mobile-run     # physics smoke CLI (prints calorimetry, TWI, GDD, cert)
make mobile-shell   # interactive shell
make mobile-down
```

## Layout

```
src/main/kotlin/id/peritanix/dape/
├── physics/Physics.kt               # TWI, Black's law, GDD, Archimedes, Stokes
├── crypto/Ed25519OfflineSigner.kt   # offline signing + CanonicalJson
└── cli/Main.kt                      # smoke CLI
src/test/kotlin/id/peritanix/dape/
├── PhysicsTest.kt                   # 22 tests
└── CryptoTest.kt                    # 5 tests
```

## Cross-platform guarantee

A certificate signed here verifies on the Go backend
(`POST /api/v1/certificates/verify`). This requires the canonical JSON float
rule to match: integral values → `x.0`, non-integral → shortest round-trip.
See `docs/PHYSICS.md` §8.

## Android layer (next)

The `mobile` compose profile runs the pure core. Building an APK requires the
Android SDK — use the `android` Docker target and mount the SDK:

```bash
make mobile-build   # gradle assembleDebug (needs ANDROID_SDK_ROOT)
make mobile-apk     # copy APK to ./dist
```

On-device the private key lives in the Android Keystore; this module uses a
seed for testability, with the same Ed25519 algorithm (BouncyCastle).

### Android app (`android/`)

A Jetpack Compose app that **consumes the shared core as source** (no logic
duplication — the physics/crypto above is the single source of truth):

```
android/app/src/main/java/id/peritanix/dape/android/
├── ui/MainActivity.kt      # Compose UI (Lands / Diagnostics / Harvest tabs)
└── data/
    ├── DapeDatabase.kt     # Room entities + DAO (sync_queue, lands, diagnoses)
    ├── SyncQueueStore.kt   # offline queue + WorkManager scheduling
    └── SyncWorker.kt       # constraints-gated delta sync (Wi-Fi/4G)
```

Key configuration:
- `minSdk 26`, `targetSdk 35`, Jetpack Compose (BOM), Room + KSP, WorkManager.
- NDK `abiFilters` for the future OpenCV C++ (burst aligner / sediment detector).
- `proguard-rules.pro` **keeps** `id.peritanix.dape.physics.**` and `.crypto.**`
  unobfuscated, because the canonical JSON float formatting must stay
  byte-identical to the Go verifier for offline certificate verification.
- **Gradle wrapper** committed (`gradlew` + `gradle-wrapper.jar`) so the project
  builds with no pre-installed Gradle: `cd mobile/android && ./gradlew assembleDebug`.

```bash
cd mobile/android && ./gradlew assembleDebug   # requires Android SDK
```

The APK build is optional for local development; `make mobile-test` (pure JVM)
is the always-available path.
