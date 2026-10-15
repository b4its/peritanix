# Keep the shared deterministic core (never obfuscate physics/crypto — the
# canonical JSON float formatting must stay byte-identical to the server).
-keep class id.peritanix.dape.physics.** { *; }
-keep class id.peritanix.dape.crypto.** { *; }
