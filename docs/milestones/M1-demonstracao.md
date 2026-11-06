# Demonstração reproduzível da M1

| Campo | Valor |
| ----- | ----- |
| Status | Implementado |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-06 |
| Depende de | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md), [Autenticação e autorização](../architecture/autenticacao-e-autorizacao-contas.md), [ADR-002](../adr/ADR-002-persistencia-postgresql.md), [Padrão de documentação](../engineering/documentation-standard.md) |
| Missão | M1 — Fundação arquitetural |

## Objetivo

Provar, a partir de um clone limpo, que a fundação da M1 funciona: build, testes,
documentação, segurança documental, cobertura e o fluxo de contas com autenticação e
autorização por proprietário.

## Pré-requisitos

- Java 21.
- PostgreSQL 16 (Docker Compose via `make up`, ou banco externo com `DATABASE_URL`,
  `DATABASE_USER` e `DATABASE_PASSWORD`).
- Python 3 para `make docs-check` e `make security-check`.
- Uma porta livre para a API (8080 por padrão; use `SERVER_PORT`).

## Passo 1 — Clonar e validar a estrutura

```sh
git clone https://github.com/Felipefsa03/PayCore.git
cd PayCore
git checkout docs/m1-demonstracao-checklist-retrospectiva
make help
make docs-check
make security-check
```

Resultado observado em 2026-11-06: 13 alvos listados, 22 arquivos Markdown com links válidos e
24 ameaças completas.

## Passo 2 — Compilar e testar

```sh
export PAYCORE_IT_JDBC_URL='jdbc:postgresql://<host>:<porta>/paycore_it'
export PAYCORE_IT_DB_USER='<usuario>'
export PAYCORE_IT_DB_PASSWORD='<senha>'
make test
make coverage-summary
```

Resultado observado: BUILD SUCCESS, 24 testes (12 unitários + 12 de integração), 0 falhas,
0 violações Checkstyle, cobertura total de 81,2% linhas e 61,8% branches.

## Passo 3 — Subir a API

Sem Docker, use um banco dedicado e a porta desejada:

```sh
export DATABASE_URL='jdbc:postgresql://<host>:<porta>/paycore_demo'
export DATABASE_USER='<usuario>'
export DATABASE_PASSWORD='<senha>'
export SERVER_PORT=8090
make run
```

`make run` compila o jar e inicia a aplicação. Aguarde `Started FinancialCoreApplication`.
O health check responde em `/actuator/health`.

## Passo 4 — Gerar tokens de laboratório

Use o script Python de
[`autenticacao-e-autorizacao-contas.md`](../architecture/autenticacao-e-autorizacao-contas.md)
com o segredo `paycore-lab-dev-only-secret-change-me-0123456789abcdef` e dois UUIDs de
proprietário (Alice e Bob).

## Passo 5 — Executar o fluxo de contas

```sh
BASE=http://localhost:8090
# 1. Criar conta: POST /accounts -> 201, ownerId = Alice, availableBalance = 0
# 2. Ler a própria conta: GET /accounts/{id} -> 200
# 3. Outro proprietário: GET /accounts/{id} com token de Bob -> 403 forbidden
# 4. Sem token: GET /accounts/{id} -> 401 unauthenticated
# 5. Moeda inválida: POST /accounts com "brl" -> 400 validation_error
# 6. Histórico: GET /accounts/{id}/postings -> 200, items vazio
```

## Resultado observado em 2026-11-06

| Passo | Resultado |
| ----- | --------- |
| `make help` | 13 alvos listados |
| `make docs-check` | 22 arquivos Markdown, todos os links válidos |
| `make security-check` | 24 ameaças completas |
| `make test` | BUILD SUCCESS; 24 testes; 0 falhas; 0 violações Checkstyle |
| `make coverage-summary` | Total 81,2% linhas / 61,8% branches |
| `POST /accounts` (Alice) | 201; `ownerId` = Alice; `availableBalance` = 0 |
| `GET /accounts/{id}` (Alice) | 200 |
| `GET /accounts/{id}` (Bob) | 403 `forbidden` |
| `GET /accounts/{id}` (sem token) | 401 `unauthenticated` |
| `POST /accounts` (moeda `brl`) | 400 `validation_error` |
| `GET /accounts/{id}/postings` | 200; lista vazia com `nextCursor: null` |
| Health check | `{"status":"UP"}` |

A conta criada na execução observada teve id
`08624b9f-de18-422d-9450-f4e10d72bbd1`.

## Limitações da demonstração

1. Sem Docker no ambiente de execução: `make up` não foi exercitado; o banco foi PostgreSQL 16
   externo. O alvo falha com mensagem explícita quando Docker está ausente.
2. Sem movimentação de valor: transferências, ledger e idempotência são decisões e desenho
   (ADR-002), não implementação. A demo cobre contas, autenticação e autorização.
3. Sem TLS: segredo e tráfego são de laboratório.
4. A porta 8080 pode estar ocupada; use `SERVER_PORT`.
5. Sem teste de carga ou resiliência: performance e recuperação são metas das M2 e M6.

## Encerramento

```sh
kill $(cat /tmp/opencode/demo-app.pid)
```

A prova automatizada equivalente está em
`services/financial-core/app/src/test/java/.../AccountApiIntegrationTest.java` e roda com
`make test-integration`.

## Referências

- [Checklist de entrega da M1](M1-checklist.md)
- [Retrospectiva da M1](M1-retrospectiva.md)
- [Autenticação e autorização](../architecture/autenticacao-e-autorizacao-contas.md)
- [Estratégia de testes](../quality/estrategia-de-testes.md)
- [Modelo de ameaças](../security/modelo-ameacas.md)
