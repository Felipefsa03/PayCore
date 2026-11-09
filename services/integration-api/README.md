# Integration API (paycore/integration-api)

Serviço TypeScript/NestJS que expõe a borda para parceiros simulados do PayCore.
A autenticação de parceiro é feita por API key; o serviço cunha um JWT HS256 de
curta duração para o proprietário do parceiro e consulta o núcleo financeiro em
seu nome. Assim, a autorização por proprietário do núcleo continua sendo a
fronteira de isolamento entre parceiros.

## Requisitos

- Node.js 22+.
- PostgreSQL acessível (o serviço cria o schema configurado de forma idempotente).

## Variáveis de ambiente

| Variável | Obrigatória | Descrição |
| --- | --- | --- |
| `PAYCORE_INTEGRATION_DB_URL` | sim | URL do PostgreSQL (ex.: `postgresql://user:pass@127.0.0.1:5432/paycore`). |
| `PAYCORE_INTEGRATION_DB_SCHEMA` | não | Schema das tabelas do serviço (padrão `integration_api`). |
| `PAYCORE_PARTNERS` | sim | JSON com a lista de parceiros (ver exemplo abaixo). |
| `PAYCORE_ALLOW_EMPTY_PARTNERS` | não | `1` permite subir sem parceiros (usado só para gerar o OpenAPI). |
| `PAYCORE_FINANCIAL_CORE_URL` | não | Base URL do núcleo financeiro (padrão `http://127.0.0.1:8080`). |
| `PAYCORE_FINANCIAL_CORE_TIMEOUT_MS` | não | Timeout da chamada ao núcleo em ms (padrão `3000`). |
| `PAYCORE_AUTH_JWT_SECRET` | não | Segredo HS256 compartilhado com o núcleo (padrão de laboratório). |
| `PAYCORE_AUTH_JWT_ISSUER` | não | Emissor do JWT (padrão `paycore-lab`). |
| `PAYCORE_RATE_LIMIT_PER_MINUTE` | não | Limite padrão por parceiro/rota (padrão `120`). |
| `PAYCORE_WEBHOOK_WORKER_ENABLED` | não | Liga o worker de entrega (padrão `1`; testes usam `0` e chamam o worker manualmente). |
| `PAYCORE_WEBHOOK_POLL_INTERVAL_MS` | não | Intervalo de varredura da fila (padrão `1000`). |
| `PAYCORE_WEBHOOK_BATCH_SIZE` | não | Entregas reivindicadas por ciclo (padrão `10`). |
| `PAYCORE_WEBHOOK_MAX_ATTEMPTS` | não | Tentativas antes da DLQ (padrão `6`). |
| `PAYCORE_WEBHOOK_TIMEOUT_MS` | não | Timeout de resolução + POST de cada tentativa (padrão `5000`). |
| `PAYCORE_WEBHOOK_BACKOFF_BASE_MS` | não | Base do backoff exponencial (padrão `1000`). |
| `PAYCORE_WEBHOOK_BACKOFF_MAX_MS` | não | Teto do backoff (padrão `60000`). |
| `PAYCORE_WEBHOOK_LOCK_TTL_MS` | não | Após quanto tempo uma entrega `delivering` órfã volta para a fila (padrão `30000`). |
| `PAYCORE_WEBHOOK_ALLOW_PRIVATE_NETWORKS` | não | `1` libera loopback/rede privada na entrega (apenas laboratório; padrão `0`). |
| `PAYCORE_WEBHOOK_KAFKA_ENABLED` | não | `1` conecta o consumidor Kafka de eventos (padrão `0`). |
| `PAYCORE_WEBHOOK_KAFKA_BROKERS` | não | Lista de brokers separada por vírgula (padrão `127.0.0.1:9092`). |
| `PAYCORE_WEBHOOK_KAFKA_GROUP_ID` | não | Grupo do consumidor (padrão `paycore-integration-webhooks`). |
| `PAYCORE_WEBHOOK_KAFKA_CLIENT_ID` | não | Client id do consumidor (padrão `paycore-integration-api`). |
| `PORT` | não | Porta HTTP (padrão `3000`). |

Exemplo de `PAYCORE_PARTNERS`:

```json
[
  {
    "id": "partner-alpha",
    "name": "Parceiro Alpha",
    "apiKey": "alpha-dev-only-key",
    "ownerId": "11111111-1111-4111-8111-111111111111",
    "rateLimitPerMinute": 120
  }
]
```

O `ownerId` precisa ser um UUID: é o `sub` do JWT usado para consultar o núcleo
financeiro. Parceiros nunca compartilham `ownerId`.

## Executando

```sh
npm ci
npm run start:dev
```

A documentação interativa fica em `GET /docs` e o documento OpenAPI em
`GET /openapi.json`. O contrato versionado é
[`contracts/openapi/integration-api.yaml`](../../contracts/openapi/integration-api.yaml).

## Webhooks confiáveis

Com `PAYCORE_WEBHOOK_KAFKA_ENABLED=1`, o serviço consome
`paycore.payments.payment-completed.v1` e `paycore.payments.payment-rejected.v1`.
Cada evento válido cria uma entrega por inscrição ativa cujo `eventTypes` inclui o tipo;
a unicidade `(subscription_id, event_id)` impede duplicação quando o Kafka reentrega.

O corpo do POST é o envelope do evento sem alterações. Os metadados vão nos cabeçalhos:

| Cabeçalho | Conteúdo |
| --- | --- |
| `X-PayCore-Webhook-Id` | Inscrição que originou a entrega. |
| `X-PayCore-Delivery-Id` | Identificador da tentativa lógica (estável entre retries). |
| `X-PayCore-Event-Id` / `X-PayCore-Event-Type` | Identidade do evento para deduplicação no receptor. |
| `X-PayCore-Attempt` | Número da tentativa (1-based). |
| `X-PayCore-Timestamp` | Unix timestamp em segundos usado na assinatura. |
| `X-PayCore-Signature` | `v1=HMAC-SHA256(secret, "${timestamp}.${corpo}")` em hexadecimal. |

O receptor deve recalcular o HMAC sobre o corpo bruto, comparar em tempo constante e
rejeitar timestamps fora da janela aceita (recomendado: 5 minutos). Retries usam o mesmo
corpo; o timestamp é renovado. HTTP 2xx encerra a entrega; timeout, erro de rede, 408, 425,
429 e 5xx são retentados com backoff exponencial; os demais 4xx/3xx vão direto para a DLQ.

A DLQ é o estado `dead`. O parceiro consulta o histórico e as tentativas em
`GET /v1/webhook-deliveries` (`?status=dead`) e `GET /v1/webhook-deliveries/{id}`, e pode
enfileirar um reenvio controlado com `POST /v1/webhook-deliveries/{id}/resend`; o reenvio
cria uma nova entrega vinculada à original, preservando o histórico. A entrega roda em
worker separado e usa `FOR UPDATE SKIP LOCKED`, permitindo mais de uma instância.

O destino é validado novamente na entrega: apenas `http(s)` público, sem credenciais,
sem redirects e com timeout. O laboratório pode liberar loopback/rede privada com
`PAYCORE_WEBHOOK_ALLOW_PRIVATE_NETWORKS=1`; fora dele, IPs privados, link-local e metadados
são bloqueados.

## Testes

Os testes de integração usam PostgreSQL real e dois stubs HTTP: um simula o
núcleo financeiro (valida o JWT emitido pelo serviço e aplica isolamento por
proprietário) e outro recebe os webhooks, verificando HMAC/timestamp e
permitindo enfileirar 200, 500 ou timeout. O worker fica desligado nos testes e
é acionado manualmente com um relógio controlado. Configure:

```sh
export PAYCORE_INTEGRATION_DB_URL='postgresql://user:pass@127.0.0.1:5432/paycore_it'
npm test
```

O schema de teste é isolado (`integration_api_test` por padrão) e truncado entre
os casos. Nenhum teste aponta para o banco da aplicação Gartrol. A suíte cobre
entrega assinada, payload idêntico em retry, HTTP 500, timeout, DLQ, reenvio
manual auditável, evento repetido, isolamento entre parceiros e bloqueio de
endereços privados.

## Decisões

- API key por parceiro com comparação em tempo constante sobre SHA-256; a chave
  nunca é logada.
- Rate limit em memória por parceiro e rota. É adequado a uma instância única de
  laboratório; múltiplas réplicas exigiriam um contador compartilhado.
- Segredo de webhook é gerado por inscrição, devolvido apenas na criação e
  persistido para assinatura das entregas. HMAC-SHA256 cobre timestamp e corpo
  bruto; o receptor deve deduplicar por `X-PayCore-Event-Id`.
- Retry usa backoff exponencial com teto; a DLQ preserva o payload e todas as
  tentativas. O reenvio manual cria uma entrega nova, sem reexecutar o evento de
  domínio nem duplicar o efeito financeiro.
- Destino é revalidado na entrega (SSRF): IP público, redirect desabilitado e
  timeout. Loopback/rede privada só com liberação explícita de laboratório.
- `X-Correlation-Id` é aceito (formato restrito), gerado quando ausente,
  devolvido na resposta e propagado ao núcleo financeiro.
