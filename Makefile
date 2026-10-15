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
	$(DC) exec -T ml pytest -q || true
	@printf "$(C_CYAN)[test]$(C_RESET) Go (go test)\n"
	$(DC) exec -T api go test ./... || true

verify: ## Run full verification: unit tests + E2E smoke + calibration + chaos
	@printf "$(C_CYAN)[verify]$(C_RESET) unit tests\n"
	$(DC) exec -T ml pytest -q || true
	$(DC) exec -T api go test ./... || true
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
        mobile-test mobile-down mobile-apk

mobile-up: init ## Start the mobile dev toolchain container
	$(DC) --profile mobile up -d mobile
	@printf "$(C_GREEN)[mobile]$(C_RESET) toolchain up. Use 'make mobile-shell'.\n"

mobile-build: init ## Build the Android debug APK (requires Android SDK)
	$(DC) --profile mobile run --rm mobile ./gradlew --no-daemon assembleDebug

mobile-apk: init ## Copy the built debug APK to ./dist
	@mkdir -p dist
	$(DC) --profile mobile run --rm mobile \
		sh -c "cp -v app/build/outputs/apk/debug/app-debug.apk /out/app-debug.apk" \
		-v "$(PWD)/dist:/out"
	@printf "$(C_GREEN)[mobile]$(C_RESET) APK -> ./dist/app-debug.apk\n"

mobile-restart: init ## Restart the mobile toolchain
	$(DC) --profile mobile restart mobile

mobile-logs: init ## Tail mobile toolchain logs
	$(DC) --profile mobile logs -f --tail=200 mobile

mobile-shell: init ## Shell into the mobile toolchain
	$(DC) --profile mobile exec mobile bash

mobile-test: init ## Run JVM unit tests for the pure-Kotlin physics core
	$(DC) --profile mobile run --rm mobile ./gradlew --no-daemon testDebugUnitTest 2>/dev/null || \
	$(DC) --profile mobile run --rm mobile gradle --no-daemon test

mobile-run: init ## Run the mobile physics smoke CLI
	$(DC) --profile mobile run --rm mobile gradle --no-daemon -q run --args=""

mobile-down: init ## Stop the mobile toolchain
	$(DC) --profile mobile down
