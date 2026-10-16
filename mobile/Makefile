# =============================================================================
# DAPE Mobile — Android-native Makefile
# -----------------------------------------------------------------------------
# Host-side Android workflow (Gradle + Android SDK + adb). Use this when you
# have the Android SDK installed locally and want to build, install and run the
# app on a device/emulator. It mirrors the containerized `make mobile-*` targets
# in the root Makefile, but talks to the local SDK instead of Docker.
#
#   cd mobile && make help
#
# Everything is discoverable: run `make doctor` first to check the toolchain.
# =============================================================================

SHELL := /bin/bash
.DEFAULT_GOAL := help

# --- Paths -------------------------------------------------------------------
MOBILE_DIR   := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ANDROID_DIR  := $(MOBILE_DIR)/android
WRAPPER      := $(MOBILE_DIR)/gradlew

# --- Android SDK discovery ---------------------------------------------------
# Prefer $ANDROID_HOME / $ANDROID_SDK_ROOT, then common defaults.
SDK ?= $(if $(ANDROID_HOME),$(ANDROID_HOME),$(if $(ANDROID_SDK_ROOT),$(ANDROID_SDK_ROOT),$(HOME)/Android/Sdk))
ADB ?= $(SDK)/platform-tools/adb
EMULATOR ?= $(SDK)/emulator/emulator
SDKMANAGER ?= $(SDK)/cmdline-tools/latest/bin/sdkmanager
AVDMANAGER ?= $(SDK)/cmdline-tools/latest/bin/avdmanager

# --- App identity ------------------------------------------------------------
APP_ID      ?= id.peritanix.dape
APK_DEBUG   := $(ANDROID_DIR)/app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE := $(ANDROID_DIR)/app/build/outputs/apk/release/app-release-unsigned.apk
MAIN_ACTIVITY := id.peritanix.dape.android.ui.MainActivity
AVD         ?= $(shell $(EMULATOR) -list-avds 2>/dev/null | head -1)

# --- Gradle invocation -------------------------------------------------------
GRADLE      ?= $(WRAPPER)
GFLAGS      ?= --no-daemon
# Project build cache: keep it OUT of android/.gradle so a root-owned cache from
# the Docker toolchain (make mobile-*) never blocks a host build (Permission
# denied on .gradle/*.lock). Host builds write to .host-cache/ instead.
PROJ_CACHE  ?= $(MOBILE_DIR)/.host-cache
GRUN        := cd $(ANDROID_DIR) && $(GRADLE) $(GFLAGS) --project-cache-dir $(PROJ_CACHE)

# Colours
C_RESET := \033[0m
C_BOLD  := \033[1m
C_CYAN  := \033[36m
C_GREEN := \033[32m
C_YELLOW:= \033[33m
C_RED   := \033[31m

.PHONY: help

help: ## Tampilkan semua target
	@printf "$(C_BOLD)DAPE Mobile — Android targets$(C_RESET)\n\n"
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  $(C_CYAN)%-16s$(C_RESET) %s\n", $$1, $$2}'
	@printf "\n$(C_YELLOW)Tip:$(C_RESET) jalankan 'make doctor' dulu.\n"

# =============================================================================
# Diagnostics
# =============================================================================

.PHONY: doctor

doctor: ## Cek toolchain (java, sdk, adb, emulator, avd)
	@printf "$(C_BOLD)Mengecek toolchain Android...$(C_RESET)\n"
	@command -v java >/dev/null 2>&1 && printf "  $(C_GREEN)ok$(C_RESET)  java (%s)\n" "$$(java -version 2>&1 | head -1)" || printf "  $(C_RED)MISSING$(C_RESET) java (JDK 17+)\n"
	@printf "  SDK dir: %s\n" "$(SDK)"
	@[ -d "$(SDK)" ] && printf "  $(C_GREEN)ok$(C_RESET)  Android SDK\n" || printf "  $(C_RED)MISSING$(C_RESET) Android SDK ($(SDK)) — set ANDROID_HOME\n"
	@[ -x "$(ADB)" ] && printf "  $(C_GREEN)ok$(C_RESET)  adb (%s)\n" "$$($(ADB) version 2>/dev/null | head -1 | tr -d '\r')" || printf "  $(C_RED)MISSING$(C_RESET) adb ($(ADB))\n"
	@[ -x "$(EMULATOR)" ] && printf "  $(C_GREEN)ok$(C_RESET)  emulator\n" || printf "  $(C_YELLOW)warn$(C_RESET)  emulator binary ($(EMULATOR)) — perlu paket 'emulator'\n"
	@[ -x "$(SDKMANAGER)" ] && printf "  $(C_GREEN)ok$(C_RESET)  sdkmanager\n" || printf "  $(C_YELLOW)warn$(C_RESET)  sdkmanager ($(SDKMANAGER))\n"
	@[ -x "$(WRAPPER)" ] && printf "  $(C_GREEN)ok$(C_RESET)  gradle wrapper\n" || printf "  $(C_RED)MISSING$(C_RESET) gradle wrapper ($(WRAPPER))\n"
	@n=$$($(EMULATOR) -list-avds 2>/dev/null | wc -l); \
		[ "$$n" -gt 0 ] && printf "  $(C_GREEN)ok$(C_RESET)  %s AVD terdaftar\n" "$$n" \
		|| printf "  $(C_YELLOW)warn$(C_RESET)  belum ada AVD (buat via Android Studio / avdmanager)\n"
	@d=$$($(ADB) devices 2>/dev/null | grep -w device | wc -l); \
		[ "$$d" -gt 0 ] && printf "  $(C_GREEN)ok$(C_RESET)  %s device terhubung\n" "$$d" \
		|| printf "  $(C_YELLOW)warn$(C_RESET)  tidak ada device/emulator terhubung\n"

.PHONY: info

info: ## Info detail proyek & aplikasi (versi, SDK, git, SHA-1/SHA-256 signing)
	@printf "$(C_BOLD)═══ DAPE Mobile — Info ═══$(C_RESET)\n"
	@printf "  $(C_CYAN)Aplikasi$(C_RESET)\n"
	@printf "    applicationId : %s\n" "$(APP_ID)"
	@printf "    activity      : %s\n" "$(MAIN_ACTIVITY)"
	@printf "    minSdk        : %s\n" "$$(grep -oE 'minSdk *= *[0-9]+' $(ANDROID_DIR)/app/build.gradle.kts | grep -oE '[0-9]+')"
	@printf "    targetSdk     : %s\n" "$$(grep -oE 'targetSdk *= *[0-9]+' $(ANDROID_DIR)/app/build.gradle.kts | grep -oE '[0-9]+')"
	@printf "    compileSdk    : %s\n" "$$(grep -oE 'compileSdk *= *[0-9]+' $(ANDROID_DIR)/app/build.gradle.kts | grep -oE '[0-9]+')"
	@printf "    versionName   : %s\n" "$$(grep -oE 'versionName *= *[\"][^\"]+[\"]' $(ANDROID_DIR)/app/build.gradle.kts | head -1 | sed -E 's/.*[\"]([^\"]+)[\"].*/\1/')"
	@printf "    versionCode   : %s\n" "$$(grep -oE 'versionCode *= *[0-9]+' $(ANDROID_DIR)/app/build.gradle.kts | grep -oE '[0-9]+')"
	@printf "  $(C_CYAN)Toolchain$(C_RESET)\n"
	@printf "    java          : %s\n" "$$(java -version 2>&1 | head -1 || echo 'n/a')"
	@printf "    SDK           : %s\n" "$(SDK)"
	@printf "    gradle        : %s\n" "$$($(GRADLE) --version 2>/dev/null | grep -i '^Gradle' | head -1 || echo 'n/a')"
	@printf "  $(C_CYAN)Git$(C_RESET)\n"
	@printf "    branch        : %s\n" "$$(git -C $(MOBILE_DIR) rev-parse --abbrev-ref HEAD 2>/dev/null || echo n/a)"
	@printf "    commit        : %s\n" "$$(git -C $(MOBILE_DIR) rev-parse --short HEAD 2>/dev/null || echo n/a)"
	@printf "  $(C_CYAN)Signing (debug keystore)$(C_RESET)\n"
	@[ -f "$$HOME/.android/debug.keystore" ] && keytool -list -v -keystore "$$HOME/.android/debug.keystore" -alias androiddebugkey -storepass android -keypass android 2>/dev/null | grep -E 'SHA1:|SHA256:' | sed 's/^/    /' \
		|| printf "    (debug.keystore belum ada — akan dibuat saat build pertama)\n"

# =============================================================================
# Device / emulator
# =============================================================================

.PHONY: device-status

device-status: ## Tampilkan device/emulator terhubung beserta detail (model, Android, tipe)
	@printf "$(C_BOLD)Device terhubung:$(C_RESET)\n"
	@[ -x "$(ADB)" ] || { printf "  $(C_RED)adb tidak ditemukan$(C_RESET)\n"; exit 0; }
	@any=0; \
	for s in $$($(ADB) devices | awk 'NR>1 && $$2=="device" {print $$1}'); do \
		any=1; \
		model=$$($(ADB) -s $$s shell getprop ro.product.model 2>/dev/null | tr -d '\r'); \
		rel=$$($(ADB) -s $$s shell getprop ro.build.version.release 2>/dev/null | tr -d '\r'); \
		sdk=$$($(ADB) -s $$s shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r'); \
		abi=$$($(ADB) -s $$s shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r'); \
		typ=$$(case "$$s" in emulator-*) echo emulator ;; *) echo fisik ;; esac); \
		printf "  $(C_GREEN)●$(C_RESET) %s\n" "$$s"; \
		printf "      model=$$model · Android $$rel (SDK $$sdk) · $$abi · $$typ\n"; \
	done; \
	[ "$$any" -eq 0 ] && printf "  $(C_YELLOW)(tidak ada device terhubung)$(C_RESET)\n" || true

.PHONY: wait-device

wait-device: ## Tunggu device/emulator terhubung
	@printf "$(C_CYAN)[mobile]$(C_RESET) menunggu device/emulator...\n"
	@[ -x "$(ADB)" ] || { printf "  $(C_RED)adb tidak ditemukan$(C_RESET)\n"; exit 1; }
	$(ADB) wait-for-device
	@until [ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
	@printf "  $(C_GREEN)ok$(C_RESET) device siap\n"

.PHONY: emulator-list

emulator-list: ## Tampilkan daftar AVD
	@if [ ! -x "$(EMULATOR)" ]; then printf "  $(C_RED)emulator binary tidak ditemukan ($(EMULATOR))$(C_RESET)\n"; exit 0; fi; \
	printf "$(C_BOLD)AVD tersedia:$(C_RESET)\n"; \
	avds=$$("$(EMULATOR)" -list-avds 2>/dev/null); \
	[ -n "$$avds" ] && echo "$$avds" | sed 's/^/  - /' || printf "  $(C_YELLOW)(belum ada AVD)$(C_RESET)\n"

# =============================================================================
# Lifecycle (build / install / run)
# =============================================================================

.PHONY: up

up: ## Build + install + launch di device aktif; nyalakan emulator bila tidak ada device
	@if [ -x "$(ADB)" ] && [ -n "$$($(ADB) devices | awk 'NR>1 && $$2=="device' | head -1)" ]; then \
		printf "$(C_GREEN)[mobile]$(C_RESET) device sudah terhubung, langsung build+install+run.\n"; \
		$(MAKE) --no-print-directory run; \
	else \
		printf "$(C_YELLOW)[mobile]$(C_RESET) tidak ada device — mencoba menyalakan emulator...\n"; \
		$(MAKE) --no-print-directory emulator; \
		$(MAKE) --no-print-directory wait-device; \
		$(MAKE) --no-print-directory run; \
	fi

.PHONY: emulator

emulator: ## Nyalakan AVD di latar belakang (AVD=<nama> untuk memilih)
	@[ -x "$(EMULATOR)" ] || { printf "  $(C_RED)emulator binary tidak ditemukan ($(EMULATOR))$(C_RESET)\n"; exit 1; }
	@if [ -z "$(AVD)" ]; then printf "  $(C_RED)tidak ada AVD; buat dulu lewat Android Studio / avdmanager$(C_RESET)\n"; exit 1; fi
	@printf "$(C_CYAN)[mobile]$(C_RESET) menyalakan AVD '%s'...\n" "$(AVD)"
	@nohup "$(EMULATOR)" -avd "$(AVD)" -no-snapshot-save >/tmp/dape-emulator.log 2>&1 & echo "  pid $$! (log: /tmp/dape-emulator.log)"

.PHONY: down

down: ## Matikan emulator yang berjalan
	@printf "$(C_CYAN)[mobile]$(C_RESET) mematikan emulator...\n"
	@[ -x "$(ADB)" ] && $(ADB) devices | awk 'NR>1 && $$1 ~ /^emulator-/ {print $$1}' | while read -r s; do \
		printf "  stopping %s\n" "$$s"; $(ADB) -s "$$s" emu kill; \
	done || true

.PHONY: build apk

build: ## Build APK debug
	@printf "$(C_CYAN)[mobile]$(C_RESET) build APK debug...\n"
	$(GRUN) assembleDebug
	@printf "$(C_GREEN)[mobile]$(C_RESET) APK -> %s\n" "$(APK_DEBUG)"

apk: build ## Alias: Build APK debug

.PHONY: release

release: ## Build APK release
	@printf "$(C_CYAN)[mobile]$(C_RESET) build APK release...\n"
	$(GRUN) assembleRelease
	@printf "$(C_GREEN)[mobile]$(C_RESET) APK -> %s\n" "$(APK_RELEASE)"

.PHONY: rebuild

rebuild: ## Clean lalu build ulang
	@printf "$(C_CYAN)[mobile]$(C_RESET) clean + build ulang...\n"
	$(GRUN) clean assembleDebug

.PHONY: install

install: build ## Install APK debug ke device aktif
	@printf "$(C_CYAN)[mobile]$(C_RESET) install %s ...\n" "$(APK_DEBUG)"
	$(ADB) install -r "$(APK_DEBUG)"
	@printf "$(C_GREEN)[mobile]$(C_RESET) terinstall.\n"

.PHONY: uninstall

uninstall: ## Hapus aplikasi dari device aktif
	@printf "$(C_CYAN)[mobile]$(C_RESET) uninstall %s ...\n" "$(APP_ID)"
	$(ADB) uninstall "$(APP_ID)" || printf "  (aplikasi tidak terpasang)\n"

.PHONY: reinstall

reinstall: ## Uninstall lalu install ulang (atasi konflik versi/signature)
	@printf "$(C_CYAN)[mobile]$(C_RESET) reinstall (uninstall + install)...\n"
	-$(ADB) uninstall "$(APP_ID)"
	$(MAKE) --no-print-directory install
	@printf "$(C_GREEN)[mobile]$(C_RESET) reinstall selesai.\n"

.PHONY: run

run: install ## Install + launch aplikasi
	@printf "$(C_CYAN)[mobile]$(C_RESET) launching %s ...\n" "$(MAIN_ACTIVITY)"
	$(ADB) shell am start -n "$(APP_ID)/$(MAIN_ACTIVITY)"
	@printf "$(C_GREEN)[mobile]$(C_RESET) aplikasi diluncurkan.\n"

# =============================================================================
# Tests / lint
# =============================================================================

.PHONY: test

test: ## Unit test (host JVM)
	@printf "$(C_CYAN)[mobile]$(C_RESET) unit test debug...\n"
	$(GRUN) testDebugUnitTest
	@printf "$(C_GREEN)[mobile]$(C_RESET) unit test selesai.\n"

.PHONY: uitest

uitest: ## Instrumented test (butuh device/emulator)
	@printf "$(C_CYAN)[mobile]$(C_RESET) instrumented test (connectedDebugAndroidTest)...\n"
	$(GRUN) connectedDebugAndroidTest

.PHONY: lint

lint: ## Android lint
	@printf "$(C_CYAN)[mobile]$(C_RESET) android lint...\n"
	$(GRUN) lintDebug

.PHONY: deps

deps: ## Lihat dependency runtime debug
	@printf "$(C_CYAN)[mobile]$(C_RESET) dependency runtime debug...\n"
	$(GRUN) app:dependencies --configuration debugRuntimeClasspath

# =============================================================================
# Housekeeping
# =============================================================================

.PHONY: clean

clean: ## Hapus hasil build
	@printf "$(C_CYAN)[mobile]$(C_RESET) membersihkan hasil build...\n"
	$(GRUN) clean || true
	rm -rf $(ANDROID_DIR)/app/build $(ANDROID_DIR)/build $(MOBILE_DIR)/build $(PROJ_CACHE) 2>/dev/null || true
	@printf "$(C_GREEN)[mobile]$(C_RESET) bersih.\n"
