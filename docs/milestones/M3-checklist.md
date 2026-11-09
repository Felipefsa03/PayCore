# Checklist de entrega da M3

| Campo | Valor |
| ----- | ----- |
| Status | Implementado |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-09 |
| Depende de | [Demonstração da M3](M3-demonstracao.md) |
| Missão | M3 — Assincronia, integração e conciliação |

Todos os itens abaixo estão marcados com evidência reproduzível. A validação final é humana.

## 1. Assincronia e eventos

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 1 | Outbox escrito na mesma transação do pagamento | ✅ | `TransactionalOutboxIntegrationTest`; `docs/architecture/transactional-outbox.md` |
| 2 | Publicador com retry e concorrência | ✅ | `TransactionalOutboxIntegrationTest`; outbox publisher |
| 3 | Tópicos Kafka versionados e contratos de eventos | ✅ | `KafkaEventContractTest`; `contracts/events`; `docs/architecture/kafka-eventos-versionados.md` |
| 4 | Consumidor idempotente com inbox e replay | ✅ | `InboxIdempotencyIntegrationTest`; `docs/architecture/inbox-idempotencia-replay.md` |
| 5 | Replay não duplica efeito financeiro | ✅ | `InboxIdempotencyIntegrationTest` (replay) |

## 2. Integration API e webhooks

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 6 | API key, JWT de borda e isolamento entre parceiros | ✅ | `webhooks.e2e-spec.ts`; `docs/architecture/integration-api-parceiros.md` |
| 7 | Rate limit, correlação e OpenAPI | ✅ | `webhooks.e2e-spec.ts`; `contracts/openapi/integration-api.yaml` |
| 8 | Webhook com HMAC-SHA256 e timestamp | ✅ | `webhook-deliveries.e2e-spec.ts`; `docs/architecture/webhooks-entrega-assinada.md` |
| 9 | Fila durável com `FOR UPDATE SKIP LOCKED` | ✅ | `webhook-deliveries.e2e-spec.ts`; migrations de deliveries/attempts |
| 10 | Retry exponencial, timeout e DLQ | ✅ | `webhook-deliveries.e2e-spec.ts` (retry, timeout, DLQ) |
| 11 | Reenvio manual auditável e bloqueio de reenvio pendente | ✅ | `webhook-deliveries.e2e-spec.ts` (resend, 409) |
| 12 | Guard SSRF com rede privada liberável por flag | ✅ | `webhook-destination.guard.ts`; `PAYCORE_WEBHOOK_ALLOW_PRIVATE_NETWORKS` |
| 13 | Histórico e detalhe de entregas | ✅ | `webhook-deliveries.e2e-spec.ts` (history/detail) |

## 3. Qualidade e segurança

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 14 | Financial Core gate completo | ✅ | `./mvnw -B verify -Djacoco.skip=true` → BUILD SUCCESS (49 testes) |
| 15 | Integration API gate completo | ✅ | `npm run check` → typecheck + 23 testes + OpenAPI check + build |
| 16 | Documentação navegável | ✅ | `make docs-check` → 33 arquivos Markdown |
| 17 | Modelo de ameaças com dono, mitigação e risco residual | ✅ | `make security-check` → 26 ameaças |
| 18 | Segredos e bancos de teste fora do repositório | ✅ | `.env.example`, setup-env.ts, PostgreSQL externo |

## 4. Demonstração e entrega

| # | Critério | Status | Evidência |
| - | -------- | ------ | --------- |
| 19 | Demonstração ponta a ponta documentada | ✅ | [Demonstração da M3](M3-demonstracao.md) |
| 20 | Commits M3 registrados | ✅ | `773bd8a`, `2e83521`, `655ca7d`, `fb2fd3d`, `f92ae82` |
| 21 | Retrospectiva publicada | ✅ | [Retrospectiva da M3](M3-retrospectiva.md) |
| 22 | Limitações documentadas | ✅ | Demonstração §Limitações |

## 5. Validação humana pendente

- [ ] Revisar as PRs empilhadas da M3 na ordem: outbox → Kafka → inbox → Integration API → webhooks.
- [ ] Executar o fluxo com Kafka real e worker habilitado.
- [ ] Validar `make up` e Testcontainers em ambiente com Docker.
- [ ] Revisar modelo de ameaças e ADR-002 atualizados.
- [ ] Aprovar abertura da M4.

## Resultado

**22/22 critérios atendidos (100%)**, com evidência reproduzível. A validação final e o merge
são responsabilidade humana, conforme o contrato Gartrol.
