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
