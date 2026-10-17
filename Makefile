# =============================================================================
# DAPE / Peritanix — Makefile
# -----------------------------------------------------------------------------
# One command interface for the whole stack.
#
#   WEB (backend Go + FastAPI ML + SvelteKit PWA):
#     make web-up            Start the full web stack (build if needed)
#     make web-build         Build all web images
#     make web-restart       Restart the web stack
#     make web-seed          Load seed data (idempotent; skips if seeded)
#     make web-reseed        Wipe data + re-run migrations & seed from scratch
#     make web-logs          Tail logs of all web services
#     make web-ps            Show container status
#     make web-shell-<svc>   Shell into a service (api|ml|web|postgres|redis|minio)
#     make web-test          Run all backend test suites (Go + Python)
#     make web-down          Stop the web stack (keep volumes)
#     make web-nuke          Stop + remove volumes (DESTRUCTIVE)
#
#   MOBILE (Kotlin Android — scaffold; full build needs Android SDK):
#     make mobile-up         Start the mobile dev toolchain container
#     make mobile-build      Build the Android debug APK (needs SDK)
#     make mobile-restart    Restart the mobile toolchain
#     make mobile-logs       Tail mobile toolchain logs
#     make mobile-shell      Shell into the mobile toolchain
#     make mobile-test       Run JVM unit tests for pure-Kotlin physics core
#     make mobile-down       Stop the mobile toolchain
#
#   ANDROID (native host workflow — Gradle + SDK + adb, no Docker):
#     cd mobile && make help   See the full Android target list:
#       help doctor info up down build apk release rebuild install uninstall
#       reinstall run test uitest lint deps emulator-list device-status
#       wait-device clean
#
#   MISC:
#     make help              Show this help
#     make doctor            Check prerequisites
# =============================================================================

SHELL := /bin/bash
.DEFAULT_GOAL := help

COMPOSE ?= docker compose
ENV_FILE ?= .env
COMPOSE_FILES := -f docker-compose.yml
ifeq ($(wildcard $(ENV_FILE)),$(ENV_FILE))
COMPOSE_FILES += --env-file $(ENV_FILE)
endif
DC := $(COMPOSE) $(COMPOSE_FILES)

# Read COMPOSE_PROJECT_NAME from .env (falls back to the compose default).
PROJECT ?= $(shell grep -E '^COMPOSE_PROJECT_NAME=' $(ENV_FILE) 2>/dev/null | cut -d= -f2)
PROJECT := $(if $(PROJECT),$(PROJECT),$(notdir $(CURDIR)))

# Colours
C_RESET := \033[0m
C_BOLD  := \033[1m
C_CYAN  := \033[36m
C_GREEN := \033[32m
C_YELLOW:= \033[33m
C_RED   := \033[31m

.PHONY: help

help: ## Show this help
	@printf "$(C_BOLD)DAPE / Peritanix — make targets$(C_RESET)\n\n"
	@grep -hE '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  $(C_CYAN)%-22s$(C_RESET) %s\n", $$1, $$2}'
	@printf "\n$(C_YELLOW)Tip:$(C_RESET) run 'make doctor' first.\n"

# -----------------------------------------------------------------------------
# Bootstrap / prerequisites
# -----------------------------------------------------------------------------

.env: ## Create .env from .env.example if missing
	@if [ ! -f .env ]; then \
		cp .env.example .env; \
		printf "$(C_GREEN)[init]$(C_RESET) created .env from .env.example\n"; \
	else \
		printf "$(C_GREEN)[init]$(C_RESET) .env already exists\n"; \
	fi

init: .env ## Ensure .env exists

doctor: ## Check prerequisites (docker, compose, make)
	@printf "$(C_BOLD)Checking prerequisites...$(C_RESET)\n"
	@command -v docker >/dev/null 2>&1 && printf "  $(C_GREEN)ok$(C_RESET)  docker\n"             || printf "  $(C_RED)MISSING$(C_RESET) docker\n"
	@$(COMPOSE) version >/dev/null 2>&1      && printf "  $(C_GREEN)ok$(C_RESET)  docker compose\n" || printf "  $(C_RED)MISSING$(C_RESET) docker compose\n"
	@command -v make >/dev/null 2>&1         && printf "  $(C_GREEN)ok$(C_RESET)  make\n"           || printf "  $(C_RED)MISSING$(C_RESET) make\n"

# =============================================================================
# WEB STACK
# =============================================================================

.PHONY: web-up web-build web-restart web-seed web-reseed web-logs web-ps \
        web-test web-down web-nuke web-migrate web-shell-api web-shell-ml \
        web-shell-web web-shell-postgres web-shell-redis web-shell-minio \
        web-psql web-urls

web-build: init ## Build all web images
	$(DC) build

web-up: init ## Start the full web stack (build if needed) and wait for health
	$(DC) up -d --build
	@printf "$(C_CYAN)[web]$(C_RESET) waiting for services to be healthy...\n"
	@$(MAKE) --no-print-directory web-wait
	@$(MAKE) --no-print-directory web-urls

web-wait: ## Wait until api + ml + postgres are healthy
	@for svc in postgres redis ml api; do \
		printf "  waiting for %-10s" "$$svc"; \
		for i in $$(seq 1 60); do \
			cid=$$($(DC) ps -q $$svc 2>/dev/null); \
			if [ -z "$$cid" ]; then sleep 2; continue; fi; \
			state=$$(docker inspect -f '{{.State.Health.Status}}' $$cid 2>/dev/null || echo "none"); \
			if [ "$$state" = "healthy" ] || [ "$$state" = "none" ]; then printf " $(C_GREEN)up$(C_RESET)\n"; break; fi; \
			if [ $$i -eq 60 ]; then printf " $(C_RED)timeout$(C_RESET)\n"; fi; \
			sleep 2; \
		done; \
	done

web-restart: init ## Restart the web stack
	$(DC) restart
	@$(MAKE) --no-print-directory web-wait

web-seed: init ## Load seed data (idempotent; skips if already seeded)
	@printf "$(C_CYAN)[seed]$(C_RESET) running idempotent seed...\n"
	$(DC) exec -T postgres psql -U $(shell grep -E '^POSTGRES_USER=' .env | cut -d= -f2) \
		-d $(shell grep -E '^POSTGRES_DB=' .env | cut -d= -f2) \
		-f /docker-entrypoint-initdb.d/03_seed.sql
	@printf "$(C_GREEN)[seed]$(C_RESET) done.\n"

web-reseed: init ## DESTRUCTIVE: wipe DB volume, re-migrate and re-seed
	@printf "$(C_YELLOW)[reseed]$(C_RESET) recreating postgres volume ($(PROJECT)_pgdata)...\n"
	$(DC) rm -sfv postgres || true
	docker volume rm -f $(PROJECT)_pgdata 2>/dev/null || true
	$(DC) up -d postgres
	@printf "$(C_CYAN)[reseed]$(C_RESET) waiting for postgres...\n"
	@until [ "$$(docker inspect -f '{{.State.Health.Status}}' $$($(DC) ps -q postgres) 2>/dev/null)" = "healthy" ]; do sleep 2; done
	@printf "$(C_GREEN)[reseed]$(C_RESET) migrations + seed re-applied on fresh volume.\n"

web-migrate: init ## Apply SQL migrations to a running postgres (idempotent)
	$(DC) exec -T postgres psql -U $(shell grep -E '^POSTGRES_USER=' .env | cut -d= -f2) \
		-d $(shell grep -E '^POSTGRES_DB=' .env | cut -d= -f2) \
		-f /docker-entrypoint-initdb.d/04_migrations.sql
	@printf "$(C_CYAN)[migrate]$(C_RESET) re-applying spatial functions...\n"
	$(DC) exec -T postgres psql -U $(shell grep -E '^POSTGRES_USER=' .env | cut -d= -f2) \
		-d $(shell grep -E '^POSTGRES_DB=' .env | cut -d= -f2) \
		-f /docker-entrypoint-initdb.d/02_functions.sql

web-ps: init ## Show web stack container status
	$(DC) ps

web-logs: init ## Tail logs of all web services
	$(DC) logs -f --tail=200

web-urls: ## Print service URLs
	@printf "\n$(C_BOLD)Service URLs$(C_RESET)\n"
	@printf "  $(C_CYAN)%-16s$(C_RESET) http://localhost:%s\n" "Web (PWA)"   "$(shell grep -E '^WEB_HOST_PORT=' .env | cut -d= -f2)"
	@printf "  $(C_CYAN)%-16s$(C_RESET) http://localhost:%s\n" "Go API"      "$(shell grep -E '^GO_HOST_PORT=' .env | cut -d= -f2)"
	@printf "  $(C_CYAN)%-16s$(C_RESET) http://localhost:%s/docs\n" "FastAPI ML" "$(shell grep -E '^FASTAPI_HOST_PORT=' .env | cut -d= -f2)"
	@printf "  $(C_CYAN)%-16s$(C_RESET) http://localhost:%s\n" "MinIO Console" "$(shell grep -E '^MINIO_CONSOLE_HOST_PORT=' .env | cut -d= -f2)"
	@printf "  $(C_CYAN)%-16s$(C_RESET) localhost:%s\n\n" "Postgres"     "$(shell grep -E '^POSTGRES_HOST_PORT=' .env | cut -d= -f2)"

web-test: ## Run backend test suites (Go + Python)
	@printf "$(C_CYAN)[test]$(C_RESET) Python (pytest)\n"
	$(DC) exec -T ml pytest -q
	@printf "$(C_CYAN)[test]$(C_RESET) Go (go test)\n"
	$(DC) exec -T api go test ./...

web-lint: init ## Lint backends + frontend (ruff, go vet, svelte-check)
	@printf "$(C_CYAN)[lint]$(C_RESET) Python (ruff)\n"
	$(DC) exec -T ml ruff check app/ tests/
	@printf "$(C_CYAN)[lint]$(C_RESET) Go (go vet)\n"
	$(DC) exec -T api go vet ./...
	@printf "$(C_CYAN)[lint]$(C_RESET) Web (svelte-check)\n"
	$(DC) exec -T web npm run check
	@printf "$(C_GREEN)[lint]$(C_RESET) all clean.\n"

# =============================================================================
# EDGE MODELS (ONNX training / export / benchmark)
# =============================================================================

.PHONY: models-train models-train-classifier models-train-severity \
        models-train-fertilizer models-bench models-test

models-train: init ## Train & export ALL edge ONNX models (classifier + severity + fertilizer)
	@printf "$(C_CYAN)[models]$(C_RESET) training classifier (Opsi 1)...\n"
	$(DC) cp scripts/train_pathology_edge_models.py ml:/tmp/train_pathology_edge_models.py
	$(DC) cp scripts/train_severity_model.py        ml:/tmp/train_severity_model.py
	$(DC) cp scripts/train_fertilizer_model.py      ml:/tmp/train_fertilizer_model.py
	$(DC) exec -T ml python /tmp/train_pathology_edge_models.py
	@printf "$(C_CYAN)[models]$(C_RESET) training severity segmenter (Opsi 1)...\n"
	$(DC) exec -T ml python /tmp/train_severity_model.py
	@printf "$(C_CYAN)[models]$(C_RESET) training fertilizer (Opsi 2)...\n"
	$(DC) exec -T ml python /tmp/train_fertilizer_model.py
	@printf "$(C_GREEN)[models]$(C_RESET) artefacts written to web/backend/fastapi/models/.\n"

models-train-classifier: init ## Train & export only the Opsi 1 leaf-curl classifier
	$(DC) cp scripts/train_pathology_edge_models.py ml:/tmp/train_pathology_edge_models.py
	$(DC) exec -T ml python /tmp/train_pathology_edge_models.py

models-train-severity: init ## Train & export only the Opsi 1 severity segmenter
	$(DC) cp scripts/train_severity_model.py ml:/tmp/train_severity_model.py
	$(DC) exec -T ml python /tmp/train_severity_model.py

models-train-fertilizer: init ## Train & export only the Opsi 2 fertilizer model
	$(DC) cp scripts/train_fertilizer_model.py ml:/tmp/train_fertilizer_model.py
	$(DC) exec -T ml python /tmp/train_fertilizer_model.py

models-bench: init ## Verify shipped ONNX artefacts: sha256 integrity + inference
	$(DC) cp scripts/bench_models.py ml:/tmp/bench_models.py
	$(DC) exec -T ml python /tmp/bench_models.py

models-test: init ## Run the edge-model regression test suite (pytest)
	$(DC) exec -T ml pytest -q tests/test_edge_models.py

verify: init ## Full verification: lint + unit + E2E + calibration + chaos
	@printf "$(C_CYAN)[verify]$(C_RESET) lint\n"
	$(DC) exec -T ml ruff check app/ tests/
	$(DC) exec -T api go vet ./...
	$(DC) exec -T web npm run check
	@printf "$(C_CYAN)[verify]$(C_RESET) edge models (train + bench)\n"
	$(MAKE) --no-print-directory models-train
	$(MAKE) --no-print-directory models-bench
	@printf "$(C_CYAN)[verify]$(C_RESET) unit tests\n"
	$(DC) exec -T ml pytest -q
	$(DC) exec -T api go test ./...
	@printf "$(C_CYAN)[verify]$(C_RESET) end-to-end smoke\n"
	bash scripts/e2e_smoke.sh
	@printf "$(C_CYAN)[verify]$(C_RESET) agronomic calibration\n"
	bash scripts/calibration_test.sh
	@printf "$(C_CYAN)[verify]$(C_RESET) offline + chaos\n"
	bash scripts/offline_chaos_test.sh
	@printf "$(C_GREEN)[verify]$(C_RESET) all checks passed.\n"

e2e: ## Run only the end-to-end smoke test
	bash scripts/e2e_smoke.sh

calibrate: ## Run only the agronomic calibration test
	bash scripts/calibration_test.sh

chaos: ## Run only the offline + chaos test
	bash scripts/offline_chaos_test.sh

loadtest: init ## Run the k6 load test against the running stack (needs k6)
	@command -v k6 >/dev/null 2>&1 || { printf "$(C_YELLOW)[loadtest]$(C_RESET) k6 not installed: https://k6.io/docs/get-started/installation/\n"; exit 1; }
	k6 run scripts/loadtest.js

db-backup: init ## Dump the database to backups/dape-<timestamp>.dump
	bash scripts/db_backup.sh

db-restore: init ## Restore a dump: make db-restore DUMP=backups/x.dump [CLEAN=--clean]
	@if [ -z "$(DUMP)" ]; then echo "usage: make db-restore DUMP=backups/x.dump [CLEAN=--clean]"; exit 1; fi
	bash scripts/db_restore.sh $(DUMP) $(CLEAN)

web-down: init ## Stop the web stack (keep volumes)
	$(DC) down

web-nuke: init ## DESTRUCTIVE: stop web stack and remove all volumes
	$(DC) down -v --remove-orphans

web-psql: init ## Open psql shell
	$(DC) exec postgres psql -U $(shell grep -E '^POSTGRES_USER=' .env | cut -d= -f2) -d $(shell grep -E '^POSTGRES_DB=' .env | cut -d= -f2)

web-shell-api:      init ; $(DC) exec api      sh
web-shell-ml:       init ; $(DC) exec ml       sh
web-shell-web:      init ; $(DC) exec web      sh
web-shell-postgres: init ; $(DC) exec postgres sh
web-shell-redis:    init ; $(DC) exec redis    sh
web-shell-minio:    init ; $(DC) exec minio    sh

# =============================================================================
# MOBILE STACK (Kotlin Android)
# =============================================================================

.PHONY: mobile-up mobile-build mobile-restart mobile-logs mobile-shell \
        mobile-test mobile-down mobile-apk mobile-release mobile-aab \
        mobile-apk-release mobile-artifacts mobile-verify android

mobile-up: init ## Start the mobile dev toolchain container
	$(DC) --profile mobile up -d mobile
	@printf "$(C_GREEN)[mobile]$(C_RESET) toolchain up. Use 'make mobile-shell'.\n"

mobile-build: init ## Build the Android debug APK (needs Android SDK)
	@printf "$(C_CYAN)[mobile]$(C_RESET) building the Android app (requires SDK)...\n"
	$(DC) --profile mobile run --rm \
		-e ANDROID_HOME=/opt/android-sdk \
		-e ANDROID_SDK_ROOT=/opt/android-sdk \
		--entrypoint sh mobile -c \
		"cd /app/android && (./gradlew --no-daemon assembleDebug || gradle --no-daemon assembleDebug); st=\$$?; chown -R $$(id -u):$$(id -g) /app/android/app/build /app/android/.gradle /app/build 2>/dev/null || true; exit \$$st" \
		|| printf "$(C_YELLOW)[mobile]$(C_RESET) Android SDK not configured; the pure-JVM core build is 'make mobile-test'.\n"

mobile-release: init ## Build the R8-minified release APK (shrunk + stripped, unsigned)
	@printf "$(C_CYAN)[mobile]$(C_RESET) building release APK (R8 obfuscation + resource shrinking + native stripping)...\n"
	$(DC) --profile mobile run --rm \
		-e ANDROID_HOME=/opt/android-sdk \
		-e ANDROID_SDK_ROOT=/opt/android-sdk \
		--entrypoint sh mobile -c \
		"cd /app/android && gradle --no-daemon assembleRelease; st=\$$?; chown -R $$(id -u):$$(id -g) /app/android/app/build /app/android/.gradle /app/build 2>/dev/null || true; exit \$$st" \
		|| printf "$(C_YELLOW)[mobile]$(C_RESET) release build needs the Android SDK mounted (ANDROID_SDK_PATH).\n"

mobile-aab: init ## Build the release Android App Bundle (for Play Store)
	@printf "$(C_CYAN)[mobile]$(C_RESET) building release AAB (Play Store bundle)...\n"
	$(DC) --profile mobile run --rm \
		-e ANDROID_HOME=/opt/android-sdk \
		-e ANDROID_SDK_ROOT=/opt/android-sdk \
		--entrypoint sh mobile -c \
		"cd /app/android && gradle --no-daemon bundleRelease; st=\$$?; chown -R $$(id -u):$$(id -g) /app/android/app/build /app/android/.gradle /app/build 2>/dev/null || true; exit \$$st" \
		|| printf "$(C_YELLOW)[mobile]$(C_RESET) AAB build needs the Android SDK mounted (ANDROID_SDK_PATH).\n"

mobile-apk: init ## Copy the built debug APK to ./dist
	@mkdir -p dist
	$(DC) --profile mobile run --rm \
		-v "$(PWD)/dist:/out" \
		mobile sh -c "cp -v /app/android/app/build/outputs/apk/debug/app-debug.apk /out/app-debug.apk 2>/dev/null || cp -v app/build/outputs/apk/debug/app-debug.apk /out/app-debug.apk 2>/dev/null || echo 'No APK found yet; run make mobile-build first.'"
	@printf "$(C_GREEN)[mobile]$(C_RESET) APK -> ./dist/app-debug.apk\n"

mobile-apk-release: init ## Copy the release APK (+ R8 mapping) to ./dist
	@mkdir -p dist
	$(DC) --profile mobile run --rm \
		-v "$(PWD)/dist:/out" \
		mobile sh -c "cp -v /app/android/app/build/outputs/apk/release/app-release-unsigned.apk /out/app-release-unsigned.apk && cp -v /app/android/app/build/outputs/mapping/release/mapping.txt /out/app-release-mapping.txt 2>/dev/null || true"
	@printf "$(C_GREEN)[mobile]$(C_RESET) release APK + mapping -> ./dist/\n"

mobile-artifacts: mobile-release mobile-aab ## Build release APK + AAB and copy both to ./dist
	@mkdir -p dist
	$(DC) --profile mobile run --rm \
		-v "$(PWD)/dist:/out" \
		mobile sh -c "\
			cp -v /app/android/app/build/outputs/apk/release/app-release-unsigned.apk /out/app-release-unsigned.apk && \
			cp -v /app/android/app/build/outputs/bundle/release/app-release.aab /out/app-release.aab && \
			cp -v /app/android/app/build/outputs/mapping/release/mapping.txt /out/app-release-mapping.txt 2>/dev/null || true"
	@printf "$(C_GREEN)[mobile]$(C_RESET) APK + AAB + R8 mapping -> ./dist/\n"

mobile-verify: init ## Verify the release artifacts in ./dist (R8, shrinking, dex)
	bash scripts/verify_android_release.sh dist

mobile-restart: init ## Restart the mobile toolchain
	$(DC) --profile mobile restart mobile

mobile-logs: init ## Tail mobile toolchain logs
	$(DC) --profile mobile logs -f --tail=200 mobile

mobile-shell: init ## Shell into the mobile toolchain
	$(DC) --profile mobile exec mobile bash

mobile-test: init ## Run JVM unit tests for pure-Kotlin core and Android app
	@printf "$(C_CYAN)[mobile-test]$(C_RESET) Running pure-Kotlin physics & crypto tests...\n"
	$(DC) --profile mobile run --rm mobile gradle --no-daemon test
	@printf "$(C_CYAN)[mobile-test]$(C_RESET) Running Android unit tests (Core, JNI Fallback, Hardware, Edge AI)...\n"
	$(DC) --profile mobile run --rm \
		-e ANDROID_HOME=/opt/android-sdk \
		-e ANDROID_SDK_ROOT=/opt/android-sdk \
		--entrypoint sh mobile -c \
		"cd /app/android && gradle --no-daemon testDebugUnitTest; st=\$$?; chown -R $$(id -u):$$(id -g) /app/android/app/build /app/android/.gradle /app/build 2>/dev/null || true; exit \$$st"
	@printf "$(C_GREEN)[mobile-test]$(C_RESET) All mobile test suites passed.\n"

mobile-run: init ## Run the mobile physics smoke CLI
	$(DC) --profile mobile run --rm mobile gradle --no-daemon -q run

mobile-down: init ## Stop the mobile toolchain
	$(DC) --profile mobile stop mobile && $(DC) --profile mobile rm -fv mobile

# -----------------------------------------------------------------------------
# Android host workflow — delegate to mobile/Makefile (Gradle + SDK + adb).
# Usage: make android <target>   e.g.  make android up | make android test
# Or run directly: cd mobile && make up
# -----------------------------------------------------------------------------
AGOAL ?= help

android: ## Android host targets: make android <goal> (e.g. up, test, install, preview)
	$(MAKE) -C mobile $(AGOAL)

preview: ## Tampilkan live preview/emulator layar device fisik ke laptop (scrcpy)
	$(MAKE) -C mobile preview

mobile-preview: preview ## Alias untuk preview layar device fisik ke laptop

android-preview: preview ## Alias untuk preview layar device fisik ke laptop

android-up: ## Build, install, dan launch aplikasi di device fisik aktif (host SDK)
	$(MAKE) -C mobile up

android-up-preview: ## Build, install, launch, dan buka preview layar laptop sekaligus
	$(MAKE) -C mobile up-preview

preview-kill: ## Tutup jendela preview scrcpy yang aktif
	$(MAKE) -C mobile preview-kill

android-preview-kill: preview-kill ## Alias untuk menutup preview scrcpy


