# =============================================================================
# DAPE Android — R8 / ProGuard keep rules (release build)
# -----------------------------------------------------------------------------
# R8 obfuscates + shrinks; these rules protect what must survive by contract:
#   * the deterministic Kotlin core (physics/crypto), because the canonical
#     JSON float formatting must stay byte-identical to the server for
#     cross-platform Ed25519 certificate verification;
#   * Room (reflection-based entity/DAO bindings);
#   * WorkManager (reflection-instantiated Worker classes);
#   * the app's own entry points reached via the manifest / reflection.
# =============================================================================

# ---- Deterministic core: NEVER obfuscate (cross-platform canonical JSON) ----
-keep class id.peritanix.dape.physics.** { *; }
-keep class id.peritanix.dape.crypto.** { *; }
-keep class id.peritanix.dape.pathology.** { *; }

# ---- Keep annotations & generic signatures (Room/Compose/Kotlin metadata) ----
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations

# ---- Room ---------------------------------------------------------------
# Entities/DAOs are referenced reflectively by the generated Room impl.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ---- WorkManager --------------------------------------------------------
# Workers are instantiated reflectively from their class name.
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---- App entry points (referenced from AndroidManifest via reflection) ----
# Only the manifest-declared launcher component must keep its name; the rest of
# the UI is free to obfuscate. (Manifest: android:name=".ui.MainActivity")
-keep class id.peritanix.dape.android.ui.MainActivity { *; }
# Data layer is reached reflectively (Room DAOs/entities, WorkManager workers).
-keep class id.peritanix.dape.android.data.** { *; }
# Hardware/JNI bridges keep native symbol names stable.
-keep class id.peritanix.dape.android.core.hardware.** { *; }

# ---- Kotlin / coroutines / Compose metadata ------------------------------
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# ---- BouncyCastle (Ed25519) — keep provider classes ----------------------
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---- JNI native bridges (native method names must match C++) -------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- Strip verbose logging in release ------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
