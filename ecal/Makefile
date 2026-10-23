SHELL := /bin/bash
.DEFAULT_GOAL := help
MVN ?= mvn
NPM ?= npm
DOCKER ?= docker
BACKEND := backend/modular-monolith
.PHONY: help up down logs dev-db dev-backend dev-frontend install verify test e2e smoke benchmark
help:
	@echo 'ECAL: make up | down | logs | install | dev-db | dev-backend | dev-frontend | verify | e2e | smoke'
	@echo 'up: full Docker stack at http://localhost:8090; verify: isolated PostgreSQL tests + frontend tests/build'
up:
	$(DOCKER) compose up --build --wait --wait-timeout 180
# No -v: normal shutdown must preserve the learner\'s order history.
down:
	$(DOCKER) compose down
logs:
	$(DOCKER) compose logs -f --tail=80
install:
	$(NPM) ci --prefix frontend
	$(MVN) -f $(BACKEND)/pom.xml -DskipTests package
dev-db:
	$(DOCKER) compose -f compose.yaml -f compose.dev.yaml up -d --wait db
dev-backend:
	$(MVN) -f $(BACKEND)/pom.xml spring-boot:run
dev-frontend:
	$(NPM) run dev --prefix frontend -- --host 127.0.0.1
verify test:
	$(MVN) -f $(BACKEND)/pom.xml verify
	$(NPM) test --prefix frontend
	$(NPM) run build --prefix frontend
e2e:
	ECAL_WEB_URL=$${ECAL_WEB_URL:-http://127.0.0.1:8090} $(NPM) run test:e2e --prefix frontend
smoke:
	python3 scripts/smoke.py

benchmark:
	$(MVN) -f $(BACKEND)/pom.xml -DskipTests package
	python3 tests/performance/benchmark.py

.PHONY: phase3-up phase3-down phase3-logs phase3-verify phase3-e2e phase3-failure-lab
phase3-up:
	$(DOCKER) compose -f compose.phase3.yaml up --build --wait --wait-timeout 180
phase3-down:
	$(DOCKER) compose -f compose.phase3.yaml down
phase3-logs:
	$(DOCKER) compose -f compose.phase3.yaml logs -f --tail=80 api inventory payment
phase3-verify:
	$(MVN) -f backend/distributed/pom.xml verify
	$(NPM) test --prefix frontend
	VITE_ECAL_MODE=distributed $(NPM) run build --prefix frontend
phase3-e2e:
	ECAL_MODE=distributed ECAL_WEB_URL=$${ECAL_WEB_URL:-http://127.0.0.1:8091} $(NPM) run test:e2e --prefix frontend
phase3-failure-lab:
	python3 scripts/phase3_failure_lab.py

.PHONY: phase4-up phase4-down phase4-logs phase4-e2e phase4-failure-lab
phase4-up:
	$(DOCKER) compose -f compose.phase4.yaml up --build --wait --wait-timeout 240
phase4-down:
	$(DOCKER) compose -f compose.phase4.yaml down
phase4-logs:
	$(DOCKER) compose -f compose.phase4.yaml logs -f --tail=80 api inventory payment reporting
phase4-e2e:
	ECAL_MODE=events ECAL_WEB_URL=$${ECAL_WEB_URL:-http://127.0.0.1:8095} $(NPM) run test:e2e --prefix frontend
phase4-failure-lab:
	python3 scripts/phase4_failure_lab.py
