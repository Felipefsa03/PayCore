# Retrospectiva da M3

| Campo | Valor |
| ----- | ----- |
| Status | Implementado |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-09 |
| Depende de | [Demonstração da M3](M3-demonstracao.md), [Checklist da M3](M3-checklist.md) |
| Missão | M3 — Assincronia, integração e conciliação |

## 1. O que foi entregue

- 6 cards da M3 concluídos como `done`: outbox transacional, Kafka/tópicos versionados,
  consumidor idempotente com inbox/replay, Integration API NestJS, webhooks confiáveis com
  HMAC/retry/DLQ e demonstração/retrospectiva da M3.
- Commits de entrega encadeados:
  - `773bd8a` feat(financial-core): add transactional outbox for transfer events
  - `2e83521` feat(financial-core): publish versioned payment events to kafka
  - `655ca7d` feat(financial-core): consume payment events with idempotent inbox and replay
  - `fb2fd3d` feat(integration-api): add partner-facing nestjs integration api
  - `f92ae82` feat(integration-api): add signed webhook delivery with retry and dlq
- 49 testes automatizados no Financial Core e 23 testes e2e na Integration API.
- 0 falhas nos gates executados.
- 26 ameaças no modelo de ameaças, incluindo T-25 para webhooks.
- 33 arquivos Markdown com links válidos.

## 2. O que funcionou bem

- **Outbox + Kafka + inbox** formaram um fluxo assíncrono coerente, com contratos de evento
  versionados e replay auditável.
- **PostgreSQL externo (`PAYCORE_IT_*`)** desbloqueou os gates em ambiente sem Docker.
- **Webhook com fila durável e `FOR UPDATE SKIP LOCKED`** permitiu retry, DLQ e reenvio
  manual sem bloquear a borda.
- **HMAC sobre `${timestamp}.${body}` com metadados em cabeçalhos** manteve o corpo como
  envelope canônico do evento.
- **Checkers de docs e ameaças** transformaram critérios documentais em validação executável.

## 3. O que não funcionou / atrapalhou

- **Ausência de Docker** impediu exercitar Testcontainers e `make up`; a prova completa
  de broker/PostgreSQL fica para o CI.
- **Ausência de `gh` e token GitHub** impediu criar e aceitar PRs automaticamente; as PRs
  empilhadas dependem de ação humana.
- **Restart do Gartrol** cancelou o heartbeat do card 5 e exigiu reivindicação da tentativa.
- **Concorrência de reenvio manual** exigiu índice único parcial e tratamento explícito de
  `23505` para evitar corrida.
- **DNS rebinding** permanece como risco residual do guard SSRF.

## 4. Decisões tomadas

- PostgreSQL como fonte de verdade para deliveries, attempts e inbox.
- Kafka como barramento assíncrono com eventos versionados e partição por pagamento.
- Idempotência por `event_id` no consumidor e por chave de idempotência na borda.
- Webhook com assinatura HMAC, retry exponencial, DLQ e reenvio manual auditável.
- Corpo do webhook como envelope canônico; metadados em cabeçalhos `X-PayCore-*`.
- Rede privada liberada apenas com `PAYCORE_WEBHOOK_ALLOW_PRIVATE_NETWORKS=1`.

## 5. Ações para a M4

| Ação | Dono | Critério de conclusão |
| ---- | ---- | --------------------- |
| Abrir e revisar PRs empilhados da M3 | Backend | PRs sem conflitos e CI verde |
| Validar Kafka real e worker habilitado | Backend | Demo ponta a ponta reproduzível |
| Testar `make up` e Testcontainers com Docker | Plataforma | Evidência no relatório |
| Implementar risco, conciliação e serviços satélites | Backend | Cards da M4 concluídos |
| Automatizar criação/aceitação de PRs | Plataforma | Procedimento reproduzível |

## 6. Métricas da M3

| Métrica | Valor |
| ------- | ----- |
| Cards concluídos | 6/6 |
| Commits de entrega | 5 |
| Testes Financial Core | 49 |
| Testes Integration API | 23 |
| Falhas de teste | 0 |
| Violações Checkstyle | 0 |
| Ameaças modeladas | 26 |
| Arquivos Markdown validados | 33 |
| Alvos do Makefile | 13 |

## 7. Referências

- [Demonstração da M3](M3-demonstracao.md)
- [Checklist da M3](M3-checklist.md)
- [Transactional Outbox](../architecture/transactional-outbox.md)
- [Kafka, tópicos versionados e contratos de eventos](../architecture/kafka-eventos-versionados.md)
- [Consumidor idempotente, inbox e replay](../architecture/inbox-idempotencia-replay.md)
- [Integration API para parceiros simulados](../architecture/integration-api-parceiros.md)
- [Webhooks confiáveis: HMAC, retry e DLQ](../architecture/webhooks-entrega-assinada.md)
