plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "id.peritanix.dape"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // BouncyCastle provides Ed25519 for offline certificate signing on JVM
    // (mirrors the Android Keystore / BouncyCastle path used on-device).
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

kotlin {
    jvmToolchain(17)
}

application {
    // A tiny CLI that prints key physics outputs (useful for smoke-testing).
    mainClass.set("id.peritanix.dape.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
