# PayCore — entrada única para as tarefas do laboratório.
#
# `make help` lista os alvos. Variáveis podem ser sobrescritas na linha de comando,
# por exemplo: `make test MVN="./mvnw -o"`.

SHELL := /bin/sh
.DEFAULT_GOAL := help

SERVICE_DIR := services/financial-core
INTEGRATION_API_DIR := services/integration-api
MVN ?= ./mvnw
COMPOSE ?= docker compose -f infrastructure/docker/docker-compose.yml
PYTHON ?= python3
DOCS_CHECK := scripts/check-docs-links.py

.PHONY: help up down logs reset build test test-unit test-integration lint docs-check security-check coverage-summary run clean verify integration-api-install integration-api-check

help: ## Lista os alvos disponíveis
	@printf 'PayCore — alvos disponíveis:\n\n'
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z0-9_-]+:.*## / {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf '\nPré-requisitos: Java 21; Docker Compose para up/down; Python 3 para docs-check.\n'
	@printf 'Sem Docker, use PAYCORE_IT_* para apontar os testes a um PostgreSQL existente.\n'

up: ## Sobe o PostgreSQL local via Docker Compose
	@command -v docker >/dev/null 2>&1 || { \
		printf 'docker não encontrado. Instale Docker + Compose v2 ou use um PostgreSQL externo com PAYCORE_IT_*.\n' >&2; \
		exit 1; \
	}
	$(COMPOSE) up -d --wait

down: ## Derruba o PostgreSQL local (preserva os dados)
	@command -v docker >/dev/null 2>&1 || { printf 'docker não encontrado.\n' >&2; exit 1; }
	$(COMPOSE) down

logs: ## Acompanha os logs do PostgreSQL local
	@command -v docker >/dev/null 2>&1 || { printf 'docker não encontrado.\n' >&2; exit 1; }
	$(COMPOSE) logs -f postgres

reset: ## Derruba o PostgreSQL local e apaga o volume de dados
	@command -v docker >/dev/null 2>&1 || { printf 'docker não encontrado.\n' >&2; exit 1; }
	@printf 'Isso apaga o volume paycore-postgres-data. Continuando em 3s...\n'
	@sleep 3
	$(COMPOSE) down -v

build: ## Compila e empacota sem executar testes
	cd $(SERVICE_DIR) && $(MVN) -B -DskipTests package

lint: ## Executa o Checkstyle
	cd $(SERVICE_DIR) && $(MVN) -B checkstyle:check

test-unit: ## Executa apenas testes unitários (sem banco)
	cd $(SERVICE_DIR) && $(MVN) -B -Dtest='!*IntegrationTest,!FinancialCoreApplicationTest' -Dsurefire.failIfNoSpecifiedTests=false test

test-integration: ## Executa apenas testes de integração (Testcontainers ou PAYCORE_IT_*)
	cd $(SERVICE_DIR) && $(MVN) -B -Dtest='*IntegrationTest,FinancialCoreApplicationTest' -Dsurefire.failIfNoSpecifiedTests=false test

test: ## Gate completo: compilação, Checkstyle, testes unitários e de integração
	cd $(SERVICE_DIR) && $(MVN) -B verify

coverage-summary: ## Resume a cobertura JaCoCo por módulo (após make test)
	$(PYTHON) scripts/coverage-summary.py .

verify: test ## Alias do gate completo

docs-check: ## Valida links relativos e âncoras da documentação
	$(PYTHON) $(DOCS_CHECK) .

security-check: ## Valida dono, mitigação e risco residual de cada ameaça
	$(PYTHON) scripts/check-threat-model.py docs/security/modelo-ameacas.md

run: build ## Compila e inicia a API em :8080 (requer PostgreSQL)
	cd $(SERVICE_DIR) && java -jar app/target/app-0.1.0-SNAPSHOT.jar

clean: ## Limpa artefatos de build
	cd $(SERVICE_DIR) && $(MVN) -B clean

integration-api-install: ## Instala dependências da Integration API (npm ci)
	cd $(INTEGRATION_API_DIR) && npm ci

integration-api-check: ## Gate da Integration API (requer PAYCORE_INTEGRATION_DB_URL)
	cd $(INTEGRATION_API_DIR) && npm run check
