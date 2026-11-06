# Baseline de performance do M2

| Campo | Valor |
| ----- | ----- |
| Status | Implementado |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-06 |
| Depende de | [Estratégia de testes](estrategia-de-testes.md), [ADR-002](../adr/ADR-002-persistencia-postgresql.md) |
| Missão | M2 — Motor financeiro e consistência |

## 1. Ambiente

| Item | Valor |
| ---- | ----- |
| Hardware | Contêiner de desenvolvimento (Freedesktop SDK 26.08) |
| Java | Eclipse Adoptium 21.0.13 |
| Spring Boot | 3.3.5 |
| PostgreSQL | 16.10 (externo, porta 54329) |
| Pool Hikari | padrão (máximo 10 conexões) |
| Ferramenta de carga | `scripts/baseline-performance.java` (Java `HttpClient`, sem k6/Gatling instalado) |
| API | `http://localhost:8090`, jar construído com `make build` |

## 2. Cenários

### Cenário A — Leitura de conta

- Endpoint: `GET /accounts/{id}`
- Autenticação: Bearer JWT de laboratório
- Concorrência: 16 threads
- Requisições: 1000 (após 100 de aquecimento)
- Payload: nenhum

### Cenário B — Criação de conta

- Endpoint: `POST /accounts`
- Concorrência: 8 threads
- Requisições: 200
- Payload: `{"currency":"BRL"}`

## 3. Resultados

### Cenário A — GET /accounts/{id}

| Métrica | Valor |
| ------- | ----- |
| Requisições | 1000 |
| Erros | 0 |
| Throughput | 678,43 req/s |
| p50 | 21,008 ms |
| p95 | 43,266 ms |
| p99 | 58,693 ms |
| Mínimo | 6,413 ms |
| Máximo | 76,042 ms |

### Cenário B — POST /accounts

| Métrica | Valor |
| ------- | ----- |
| Requisições | 200 |
| Erros | 0 |
| Throughput | 463,12 req/s |
| p50 | 16,242 ms |
| p95 | 26,017 ms |
| p99 | 28,515 ms |
| Mínimo | 7,942 ms |
| Máximo | 38,852 ms |

## 4. Inspeção de índices e consultas

Índices existentes relevantes:

- `accounts`: PK `id`, `idx_accounts_owner_id (owner_id)`.
- `postings`: PK `id`, `idx_postings_journal_entry_id`, `idx_postings_account_id`,
  `idx_postings_account_created_at (account_id, created_at DESC)` (V4).
- `journal_entries`: PK `id`, unique `transfer_id`.
- `transfers`: PK `id`, unique `idempotency_key`, índices por conta de origem/destino.
- `idempotency_records`: PK `id`, unique `(owner_id, operation, idempotency_key)`,
  índice `expires_at`.

Planos observados:

- Saldo por conta (`balanceOf`) usa `idx_postings_account_id` e agrega em memória; com o índice
  composto da V4, a paginação do histórico evita ordenação explícita.
- Consulta de conta por id é PK, O(1).
- Consulta de transferência por idempotency key usa índice único.

## 5. Contenção identificada

- O pool Hikari padrão (10 conexões) é o principal limitador sob concorrência alta; o p99
  de leitura (58,7 ms) reflete espera por conexão mais do que execução SQL.
- A criação de conta é mais barata que o esperado (p99 28,5 ms) porque não há validação
  externa nem publicação de eventos.
- Não houve erros HTTP (taxa de erro 0%) em nenhum cenário.
- Ainda não há carga de transferência via HTTP (endpoint não exposto); o teste de
  concorrência em serviço (`TransferIntegrationTest`) cobre 8 threads com uma transferência
  financeira por chave compartilhada, sem sobregiro.

## 6. Metodologia

1. Aquecimento de 100 requisições antes da medição.
2. Execução com pool fixo de threads e `CountDownLatch`.
3. Medição de latência por requisição em nanossegundos, convertida para ms.
4. Percentis calculados por ordenação da amostra (p50, p95, p99).
5. Throughput = requisições / tempo total.
6. Taxa de erro = respostas não-2xx / total.
7. Sem think time; carga sustentada.

## 7. Reprodução

```sh
make build
export SERVER_PORT=8090
export DATABASE_URL='jdbc:postgresql://<host>:<porta>/paycore_demo'
export DATABASE_USER='<usuario>'
export DATABASE_PASSWORD='<senha>'
make run
javac -d /tmp scripts/baseline-performance.java
java -cp /tmp BaselinePerformance
```

## 8. Limitações

- Sem k6/Gatling instalado; o harness Java usa `HttpClient` e mede latência cliente.
- Ambiente de laboratório, sem TLS, sem rede real e sem carga de transferência HTTP.
- Pool Hikari padrão; resultados não são comparáveis a produção.
- O baseline é um ponto de partida para a M2; metas (p95 < 300 ms) serão reavaliadas com
  o endpoint de transferência e carga realista.
