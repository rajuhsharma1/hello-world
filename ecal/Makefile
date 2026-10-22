SHELL := /bin/bash
.DEFAULT_GOAL := help
MVN ?= mvn
NPM ?= npm
DOCKER ?= docker
BACKEND := backend/modular-monolith
.PHONY: help up down logs dev-db dev-backend dev-frontend install verify test e2e smoke
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
