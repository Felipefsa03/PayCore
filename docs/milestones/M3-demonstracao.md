# Demonstração reproduzível da M3

| Campo | Valor |
| ----- | ----- |
| Status | Implementado |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-09 |
| Depende de | [Transactional Outbox](../architecture/transactional-outbox.md), [Kafka, tópicos versionados e contratos de eventos](../architecture/kafka-eventos-versionados.md), [Consumidor idempotente, inbox e replay](../architecture/inbox-idempotencia-replay.md), [Integration API para parceiros simulados](../architecture/integration-api-parceiros.md), [Webhooks confiáveis: HMAC, retry e DLQ](../architecture/webhooks-entrega-assinada.md) |
| Missão | M3 — Assincronia, integração e conciliação |

## Objetivo

Provar, a partir de um clone limpo, que a M3 funciona de ponta a ponta: pagamento confirmado
gera evento no outbox, o evento é publicado em Kafka, o consumidor idempotente processa sem
duplicar efeito financeiro, a Integration API isola parceiros e entrega webhooks assinados
com retry, timeout e dead-letter queue.

## Pré-requisitos

- Java 21.
- PostgreSQL 16 dedicado para o Financial Core e para a Integration API.
- Kafka local compatível com os tópicos versionados da M3.
- Node.js 22+ para a Integration API.
- Python 3 para `make docs-check` e `make security-check`.

## Passo 1 — Validar estrutura e documentação

```sh
git clone https://github.com/Felipefsa03/PayCore.git
cd PayCore
make docs-check
make security-check
```

Resultado observado em 2026-11-09: 33 arquivos Markdown com links válidos e 26 ameaças
completas.

## Passo 2 — Gate do Financial Core

```sh
export PAYCORE_IT_JDBC_URL='jdbc:postgresql://<host>:<porta>/paycore_java_it'
export PAYCORE_IT_DB_USER='<usuario>'
export PAYCORE_IT_DB_PASSWORD='<senha>'
cd services/financial-core
./mvnw -B verify -Djacoco.skip=true
```

Resultado observado: `BUILD SUCCESS`, 49 testes, 0 falhas, 0 violações Checkstyle.

## Passo 3 — Gate da Integration API

```sh
cd services/integration-api
export PAYCORE_INTEGRATION_DB_URL='postgresql://<usuario>@<host>:<porta>/paycore_it'
npm ci
npm run check
```

Resultado observado: typecheck verde, 23 testes e2e, OpenAPI em sincronia e build TypeScript
verde. O worker de webhook fica desabilitado nos testes (`PAYCORE_WEBHOOK_WORKER_ENABLED=0`);
a entrega real usa worker habilitado.

## Passo 4 — Fluxo assíncrono ponta a ponta

1. Criar contas e executar transferência interna autenticada.
2. Confirmar que a transferência grava `outbox_events` na mesma transação.
3. Publicar o evento `payment.completed` ou `payment.rejected` no tópico versionado.
4. Consumir o evento com inbox idempotente e verificar ausência de efeito duplicado em replay.
5. Registrar a entrega na Integration API com API key de parceiro e correlação.
6. Entregar webhook assinado com `X-PayCore-Signature`, `X-PayCore-Timestamp`,
   `X-PayCore-Event-Id` e `X-PayCore-Delivery-Id`.
7. Validar retry exponencial, timeout, dead-letter queue e reenvio manual auditável.

## Resultado observado em 2026-11-09

| Passo | Resultado |
| ----- | --------- |
| `make docs-check` | 33 arquivos Markdown, todos os links válidos |
| `make security-check` | 26 ameaças com dono, mitigação e risco residual |
| Financial Core `verify` | BUILD SUCCESS; 49 testes; 0 falhas |
| Integration API `npm run check` | typecheck + 23 testes + OpenAPI check + build verde |
| Webhook assinado | HMAC-SHA256 sobre `${timestamp}.${body}`, timestamp em cabeçalho |
| Retry/DLQ | backoff exponencial, tentativas persistidas, DLQ e reenvio manual auditável |
| Isolamento de parceiro | API key, JWT de borda, rate limit e correlação por parceiro |

## Limitações da demonstração

1. Kafka e PostgreSQL externos são assumidos; `make up` depende de Docker e não foi exercitado
   neste ambiente.
2. A prova de concorrência do worker usa PostgreSQL com `FOR UPDATE SKIP LOCKED`; testes de
   carga distribuída ficam para a M6.
3. O reenvio manual exige entrega em `dead`; reenvios pendentes são bloqueados por índice
   único parcial.
4. SSRF é mitigado por resolução DNS na entrega, redirect desabilitado e timeout; DNS
   rebinding permanece como risco residual documentado.
5. A validação final e o merge das PRs são responsabilidade humana.

## Encerramento

Mantenha os bancos de teste separados do banco da aplicação e preserve os segredos fora do
repositório.
