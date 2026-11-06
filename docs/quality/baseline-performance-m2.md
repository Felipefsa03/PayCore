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

## 4. Otimização aplicada e comparação antes/depois

**Otimização:** pool Hikari configurável em `application.yml`, com padrão elevado de 10 para 32
conexões (`DATABASE_POOL_MAX`, mínimo 8). Motivação: o baseline identificou espera por conexão
como componente do p99 de leitura.

| Cenário | Métrica | Antes | Depois | Variação |
| ------- | ------- | ----- | ------ | -------- |
| GET /accounts/{id} | Throughput | 678,43 req/s | 687,67 req/s | +1,4% |
| GET /accounts/{id} | p50 | 21,008 ms | 21,571 ms | +2,7% |
| GET /accounts/{id} | p95 | 43,266 ms | 40,385 ms | -6,7% |
| GET /accounts/{id} | p99 | 58,693 ms | 52,040 ms | -11,3% |
| POST /accounts | Throughput | 463,12 req/s | 463,91 req/s | +0,2% |
| POST /accounts | p50 | 16,242 ms | 15,815 ms | -2,6% |
| POST /accounts | p95 | 26,017 ms | 26,350 ms | +1,3% |
| POST /accounts | p99 | 28,515 ms | 29,174 ms | +2,3% |

### Decisão

- O ganho foi modesto e concentrado no p99 de leitura; a criação de conta não melhorou.
- O gargalo não é apenas o pool: o tempo de serialização JSON, o filtro de segurança e a
  validação de token dominam a latência. Aumentar o pool além de 32 não deve trazer ganho
  proporcional e consome recursos.
- A configuração permanece parametrizada (`DATABASE_POOL_MAX`) para que ambientes com mais
  núcleos possam ajustar sem alterar código.
- Próxima otimização candidata, se necessário: cache de leitura de conta por id com
  invalidação explícita, ou redução de overhead de autenticação. Só vale se o p95 for
  inaceitável após o endpoint de transferência entrar em carga.

## 5. Inspeção de índices e consultas

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

## 6. Contenção identificada

- O pool Hikari padrão (10 conexões) é o principal limitador sob concorrência alta; o p99
  de leitura (58,7 ms) reflete espera por conexão mais do que execução SQL.
- A criação de conta é mais barata que o esperado (p99 28,5 ms) porque não há validação
  externa nem publicação de eventos.
- Não houve erros HTTP (taxa de erro 0%) em nenhum cenário.
- Ainda não há carga de transferência via HTTP (endpoint não exposto); o teste de
  concorrência em serviço (`TransferIntegrationTest`) cobre 8 threads com uma transferência
  financeira por chave compartilhada, sem sobregiro.

## 7. Metodologia

1. Aquecimento de 100 requisições antes da medição.
2. Execução com pool fixo de threads e `CountDownLatch`.
3. Medição de latência por requisição em nanossegundos, convertida para ms.
4. Percentis calculados por ordenação da amostra (p50, p95, p99).
5. Throughput = requisições / tempo total.
6. Taxa de erro = respostas não-2xx / total.
7. Sem think time; carga sustentada.

## 8. Reprodução

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

## 9. Limitações

- Sem k6/Gatling instalado; o harness Java usa `HttpClient` e mede latência cliente.
- Ambiente de laboratório, sem TLS, sem rede real e sem carga de transferência HTTP.
- Pool Hikari padrão; resultados não são comparáveis a produção.
- O baseline é um ponto de partida para a M2; metas (p95 < 300 ms) serão reavaliadas com
  o endpoint de transferência e carga realista.
