# Integration API para parceiros simulados

| Campo | Valor |
| ----- | ----- |
| Status | Implementado |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-09 |
| Depende de | [Autenticação e autorização de contas](autenticacao-e-autorizacao-contas.md), [Consumidor idempotente, inbox e replay](inbox-idempotencia-replay.md), [OpenAPI do núcleo](../../contracts/openapi/financial-core.yaml) |
| Missão | M3 · Mensageria e integrações (semanas 9–12) |

## Objetivo

Expor uma borda estável para parceiros simulados consultarem transações e cadastrarem
webhooks, sem duplicar a autorização do núcleo financeiro e sem permitir que um parceiro
enxergue dados de outro (RF-08, RF-11 e RNF-06).

O serviço é um processo TypeScript/NestJS separado do monólito Java. Ele existe porque a
borda de integração tem requisitos próprios — API keys, DTOs externos, rate limiting,
contrato OpenAPI e, no card seguinte, entrega assinada de webhooks — e isola a evolução
desses requisitos do núcleo contábil.

## 1. Fronteira e fluxo

```mermaid
sequenceDiagram
  participant P as Parceiro
  participant I as integration-api (NestJS)
  participant F as financial-core (Java)
  participant D as PostgreSQL

  P->>I: GET /v1/transactions/{id}<br/>X-Api-Key + X-Correlation-Id
  I->>I: autentica API key e aplica rate limit
  I->>I: cunha JWT HS256 (sub = ownerId do parceiro, exp = 60s)
  I->>F: GET /transfers/{id}<br/>Bearer JWT + X-Correlation-Id
  F->>D: consulta escopada pelo proprietário
  D-->>F: transferência ou vazio
  F-->>I: 200 / 404 / erro
  I-->>P: DTO allowlist + X-Correlation-Id
```

O `integration-api` **não acessa as tabelas do núcleo**. Ele usa o contrato HTTP existente
e delega a decisão de propriedade ao `financial-core`, cujo `sub` do JWT é o `ownerId`.
Assim, a fronteira de autorização continua tendo uma única implementação.

## 2. Autenticação e isolamento entre parceiros

- Cada parceiro tem `id`, `name`, `ownerId` e uma API key configurada por ambiente.
- A API key é comparada em tempo constante sobre SHA-256; a chave em claro não é
  persistida nem logada.
- O serviço cunha um JWT HS256 de curta duração com `iss`, `sub` (UUID do proprietário),
  `iat` e `exp`. O segredo é compartilhado com o núcleo via `PAYCORE_AUTH_JWT_SECRET`.
- Um parceiro não escolhe o `ownerId`: ele vem da configuração do servidor. Consultar a
  transação de outro parceiro resulta em `404 transaction_not_found`.
- Listagem e remoção de webhooks filtram por `partner_id`.

O cenário de confirmação do card — autenticar dois parceiros e tentar acessar dados do
outro — está coberto por `test/integration/transactions.e2e-spec.ts` e
`test/integration/webhooks.e2e-spec.ts`.

## 3. Endpoints

| Método | Rota | Descrição | Erros principais |
| ------ | ---- | --------- | ---------------- |
| `GET` | `/health` | Liveness público | — |
| `GET` | `/v1/transactions/{transactionId}` | Consulta transação e estado pelo parceiro autenticado | 400, 401, 404, 429, 503 |
| `POST` | `/v1/webhooks` | Cadastra webhook e devolve o segredo HMAC uma única vez | 400, 401, 429 |
| `GET` | `/v1/webhooks` | Lista webhooks do parceiro autenticado | 401, 429 |
| `DELETE` | `/v1/webhooks/{webhookId}` | Remove webhook do parceiro autenticado | 401, 404, 429 |

Todas as rotas de negócio exigem `X-Api-Key`. `X-Correlation-Id` é opcional: quando
ausente ou fora do formato permitido (1–100 caracteres seguros), o serviço gera um UUID,
devolve no cabeçalho e propaga ao núcleo.

Erros seguem um envelope estável:

```json
{
  "code": "validation_error",
  "message": "Requisição inválida.",
  "details": { "fields": [{ "field": "url", "errors": ["url must be a URL address"] }] },
  "correlationId": "8f7c2c26-2fd1-4f6d-9f6e-2f7a8c9d0e11"
}
```

## 4. Webhooks: cadastro e segredo

O cadastro valida:

- URL `http(s)` com host, sem credenciais embutidas e com no máximo 2048 caracteres;
- lista não vazia e sem duplicatas de `payment.completed`/`payment.rejected`;
- descrição opcional com até 200 caracteres;
- ausência de campos desconhecidos (`whitelist` + `forbidNonWhitelisted`).

O segredo (`whsec_…`, 256 bits) é gerado por inscrição, devolvido **apenas** na resposta de
criação e persistido para assinar entregas. A listagem nunca o devolve. A entrega HMAC,
retry, backoff, DLQ e reenvio manual são o escopo do card seguinte; este card entrega o
registro durável e o segredo que aquela entrega usará.

## 5. Rate limiting e correlação

- Limite por parceiro e rota em janela deslizante de 60 s; o padrão é 120 req/min e pode
  ser ajustado por parceiro (`rateLimitPerMinute`) ou por ambiente.
- Exceder o limite devolve `429 rate_limited`, `Retry-After` e `details.retryAfterSeconds`.
- O contador é em memória. Uma única instância é adequada ao laboratório; múltiplas
  réplicas exigiriam um contador compartilhado (Redis/PostgreSQL). A limitação está
  registrada em §8.
- O `X-Correlation-Id` acompanha a chamada ao núcleo, aparece nos logs estruturados de erro
  e em todas as respostas de erro.

## 6. Contrato OpenAPI

O contrato é gerado a partir dos decorators do NestJS e versionado em
[`contracts/openapi/integration-api.yaml`](../../contracts/openapi/integration-api.yaml).
`npm run openapi:check` falha quando o arquivo commitado diverge dos decorators; o CI roda
esse passo. Em execução, `GET /docs` serve o Swagger UI e `GET /openapi.json` o documento.

O contrato usa OpenAPI 3.0.0 porque o gerador do NestJS/Swagger expressa nulabilidade com
`nullable: true`. O núcleo Java mantém seu contrato 3.1 próprio.

## 7. Persistência

O serviço usa o PostgreSQL já existente no laboratório, em um schema isolado
(`PAYCORE_INTEGRATION_DB_SCHEMA`, padrão `integration_api`):

```sql
webhook_subscriptions (
  id uuid primary key,
  partner_id text not null,
  owner_id uuid not null,
  url text not null,
  event_types text[] not null,
  description text,
  active boolean not null default true,
  secret text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
)
```

As migrations são idempotentes e rodam na inicialização. O schema de teste é separado
(`integration_api_test`) e truncado entre cenários; nenhum teste usa o banco da aplicação
Gartrol.

## 8. Testes e evidências

| Camada | Evidência |
| ------ | --------- |
| Integração HTTP | `test/integration/transactions.e2e-spec.ts` — consulta própria, isolamento entre parceiros, correlação, 401, 400 e 503 |
| Integração HTTP | `test/integration/webhooks.e2e-spec.ts` — cadastro, segredo único, isolamento, DTOs, rate limit e OpenAPI |
| Upstream | `test/helpers/stub-financial-core.ts` valida assinatura/emissor do JWT e aplica isolamento por `sub` |
| Contrato | `npm run openapi:check` e teste que inspeciona paths, tags e `partnerApiKey` |
| Gate | `npm run check` (typecheck, 13 testes, OpenAPI, build) |

Comando local:

```sh
export PAYCORE_INTEGRATION_DB_URL='postgresql://user:pass@127.0.0.1:5432/paycore_it'
cd services/integration-api && npm ci && npm run check
```

## 9. Limitações aceitas

| Limitação | Por que é aceitável agora | Gatilho de revisão |
| --------- | ------------------------- | ------------------ |
| Rate limit em memória | Uma instância de laboratório | Múltiplas réplicas ou ambiente compartilhado |
| API keys em variável de ambiente | Parceiros simulados, sem dados reais | Provedor de identidade de parceiro / rotação |
| JWT de 60 s cunhado pelo serviço | Segredo compartilhado de laboratório | Identidade de workload na M5 |
| Segredo de webhook persistido em claro | Necessário para assinar no card 5 | Cofre de segredos ou envelope encryption |
| Stub HTTP nos testes | Evita subir o monólito Java em cada cenário | Teste E2E composto na demo do M3 |

## Referências

- [RFC-001 — Visão e arquitetura inicial](../rfc/RFC-001-visao-e-arquitetura-inicial.md)
- [Requisitos, invariantes e glossário](../requirements/requisitos-invariantes-glossario.md)
- [Modelo de ameaças](../security/modelo-ameacas.md)
- [README do serviço](../../services/integration-api/README.md)
