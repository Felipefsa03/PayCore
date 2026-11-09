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

## Testes

Os testes de integração usam PostgreSQL real e um servidor HTTP que simula o
núcleo financeiro (valida o JWT emitido pelo serviço e aplica isolamento por
proprietário). Configure:

```sh
export PAYCORE_INTEGRATION_DB_URL='postgresql://user:pass@127.0.0.1:5432/paycore_it'
npm test
```

O schema de teste é isolado (`integration_api_test` por padrão) e truncado entre
os casos. Nenhum teste aponta para o banco da aplicação Gartrol.

## Decisões

- API key por parceiro com comparação em tempo constante sobre SHA-256; a chave
  nunca é logada.
- Rate limit em memória por parceiro e rota. É adequado a uma instância única de
  laboratório; múltiplas réplicas exigiriam um contador compartilhado.
- Segredo de webhook é gerado por inscrição, devolvido apenas na criação e
  persistido para assinatura das entregas (card de webhooks).
- `X-Correlation-Id` é aceito (formato restrito), gerado quando ausente,
  devolvido na resposta e propagado ao núcleo financeiro.
