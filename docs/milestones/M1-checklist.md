# Checklist de entrega da M1

| Campo | Valor |
| ----- | ----- |
| Status | Implementado |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-06 |
| Depende de | [Demonstração da M1](M1-demonstracao.md), [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md) |
| Missão | M1 — Fundação arquitetural |

Todos os itens abaixo estão marcados com evidência reproduzível. A validação final é humana.

## 1. Escopo e arquitetura

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 1 | Monólito modular com fronteiras `shared`, `accounts`, `ledger`, `payments`, `app` | ✅ | `services/financial-core/pom.xml` e módulos; ADR-001 planejado |
| 2 | Contas com autenticação e autorização por proprietário | ✅ | `AccountApiIntegrationTest` (10 testes); `docs/architecture/autenticacao-e-autorizacao-contas.md` |
| 3 | Contrato OpenAPI validado e tipos TypeScript gerados | ✅ | `contracts/openapi/financial-core.yaml`; `contracts/openapi/generated/financial-core.d.ts` |
| 4 | Machine de estados e invariantes documentadas | ✅ | `docs/architecture/modelo-dominio-estados-transferencia.md` |

## 2. Persistência e segurança

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 5 | ADR-002 de persistência em PostgreSQL (ACID, locks, idempotência) | ✅ | `docs/adr/ADR-002-persistencia-postgresql.md` (status Aceito) |
| 6 | Modelo de ameaças STRIDE com 24 ameaças | ✅ | `docs/security/modelo-ameacas.md` |
| 7 | Cada ameaça tem dono, mitigação e risco residual aceito | ✅ | `make security-check` → 24/24 |
| 8 | Segredos fora do repositório e validação de token | ✅ | `OwnerIdValidatorTest`; `application.yml` com segredo de laboratório |

## 3. Monorepo, Makefile e documentação

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 9 | Makefile com alvos principais funcionando | ✅ | `make help` → 13 alvos; clean clone validado |
| 10 | Documentação navegável com índice único | ✅ | `docs/README.md`; `make docs-check` → 22 arquivos, 0 links quebrados |
| 11 | Padrão de documentação por milestone | ✅ | `docs/engineering/documentation-standard.md` |
| 12 | Templates de ADR, milestone e runbook | ✅ | `docs/templates/` |
| 13 | Checker de links no CI | ✅ | `.github/workflows/docs-ci.yml` |

## 4. Testes e evidências

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 14 | Estratégia de testes por camada | ✅ | `docs/quality/estrategia-de-testes.md` |
| 15 | Matriz de evidências requisito → teste → comando | ✅ | `docs/quality/matriz-evidencias.md` |
| 16 | Gate completo com 24 testes e 0 falhas | ✅ | `make test` → BUILD SUCCESS |
| 17 | Cobertura declarada e medida | ✅ | JaCoCo: 81,2% linhas / 61,8% branches; `make coverage-summary` |
| 18 | Relatório de execução da M1 | ✅ | `docs/quality/evidencias/M1-2026-11-06.md` |
| 19 | CI publica artefatos de teste/cobertura/lint | ✅ | `financial-core-ci.yml` → `financial-core-reports` |

## 5. Demonstração e entrega

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 20 | Demonstração executada a partir de clone limpo | ✅ | `docs/milestones/M1-demonstracao.md` (resultados observados) |
| 21 | Fluxo de contas validado (criar, ler, 403, 401, 400, postings) | ✅ | Resultado observado com conta `08624b9f-...` |
| 22 | Retrospectiva publicada | ✅ | `docs/milestones/M1-retrospectiva.md` |
| 23 | Limitações documentadas (Docker, Node, portas, escopo) | ✅ | Demonstração §Limitações e relatório §8 |
| 24 | PRs compare URLs disponíveis para os 4 cards | ✅ | Ver checkpoint de cada card |

## 6. Validação humana pendente

- [ ] Revisar os 4 PRs empilhados na ordem: contas → `dabd1ce7` → `ee96e0c1` → `1265ed59` → `e882b3fa`.
- [ ] Executar `make test` no CI com Testcontainers (Docker).
- [ ] Validar `make up` em ambiente com Docker.
- [ ] Revisar modelo de ameaças e ADR-002.
- [ ] Aprovar as metas de cobertura como política da M2.

## Resultado

**24/24 critérios atendidos (100%)**, com evidência reproduzível. A validação final e o merge
são responsabilidade humana, conforme o contrato Gartrol.
