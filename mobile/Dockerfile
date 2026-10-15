# =============================================================================
# DAPE Mobile — Kotlin/JVM toolchain (pure physics + crypto core)
# -----------------------------------------------------------------------------
# This image runs the *toolchain* for the pure-Kotlin core so `make mobile-test`
# and `make mobile-run` work without the Android SDK. Drop the SDK in (target
# `android`) to build the actual APK.
# =============================================================================
FROM eclipse-temurin:17-jdk AS base

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip bash git \
    && rm -rf /var/lib/apt/lists/*

# Gradle (pinned). The wrapper downloads its own distribution, but having a
# system gradle lets `gradle wrapper` bootstrap cleanly.
ENV GRADLE_VERSION=8.10.2 \
    GRADLE_HOME=/opt/gradle \
    PATH=/opt/gradle/bin:$PATH
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv /opt/gradle-${GRADLE_VERSION} /opt/gradle \
    && rm /tmp/gradle.zip \
    && gradle --version

WORKDIR /app
COPY . .

# Pre-fetch dependencies (cached layer).
RUN gradle --no-daemon dependencies --quiet || true

# ---------------------------------------------------------------------------
FROM base AS dev
CMD ["sleep", "infinity"]

# ---------------------------------------------------------------------------
# Android build target (optional; requires the Android SDK to be mounted).
FROM base AS android
ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p ${ANDROID_SDK_ROOT}
CMD ["gradle", "--no-daemon", "assembleDebug"]
