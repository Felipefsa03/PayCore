# Consumidor idempotente, inbox e replay

| Campo      | Valor                                                                                                                         |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Status     | Implementado                                                                                                                  |
| Autor      | Luis Felipe (com apoio de execução via Gartrol/OpenCode)                                                                      |
| Data       | 2026-11-07                                                                                                                    |
| Depende de | [Kafka, tópicos versionados e contratos de eventos](kafka-eventos-versionados.md), [Transactional Outbox](transactional-outbox.md) |
| Missão     | M3 · Mensageria e integrações (semanas 9–12)                                                                                  |

## Objetivo

Consumir os eventos de pagamento com semântica **at-least-once** sem duplicar
efeito financeiro: cada entrega é registrada em uma inbox deduplicada por
`event_id`, o efeito é idempotente e o replay é auditável.

## 1. Fluxo

```text
Kafka (payment-completed/rejected v1)
        │
        ▼
PaymentEventConsumer ──▶ InboxService.handle
                              │
                    INSERT inbox_events ... ON CONFLICT (event_id) DO NOTHING
                              │
                 inseriu? ───┴──▶ não: DUPLICATE (nenhum efeito)
                              │
                             sim
                              ▼
                    PaymentEventHandler
                     completed → settlements (payment_id único)
                     rejected  → sem efeito financeiro
                              │
                              ▼
                    marca PROCESSED (mesma transação)
```

A inbox e o efeito financeiro são gravados na **mesma transação**. Se o
efeito falhar, a transação inteira reverte e o Kafka reentrega o evento; não
existe estado parcial.

## 2. Deduplicação

- `inbox_events.event_id` é único. A inserção usa `ON CONFLICT DO NOTHING`, o
  que resolve tanto entrega repetida quanto dois consumidores concorrentes.
- `settlements.payment_id` e `settlements.event_id` também são únicos: mesmo que
  um replay force o processamento, o efeito financeiro não duplica. Essa é a
  segunda barreira (defesa em profundidade).

## 3. Replay

`POST /inbox/events/{eventId}/replay` (bearer token) reexecuta o handler e
registra uma linha em `inbox_replays` com quem pediu, quando e o resultado
(`SETTLED`, `ALREADY_SETTLED` ou `NO_FINANCIAL_EFFECT`). O serviço valida que o
pagamento pertence ao proprietário do token; eventos de outro dono respondem
`404`.

O replay não depende do Kafka: o envelope completo fica em
`inbox_events.payload`. Ele é seguro porque o handler é idempotente e porque a
deduplicação financeira é feita pelo banco, não pela aplicação.

## 4. Configuração

| Propriedade                      | Padrão          | Efeito                                             |
| -------------------------------- | --------------- | -------------------------------------------------- |
| `paycore.inbox.consumer-enabled` | `false`         | Liga o listener de Kafka.                          |
| `paycore.inbox.consumer-group`   | `paycore-inbox` | Grupo de consumo; offsets commitados por registro. |

Para rodar localmente com o broker do `docker compose`:

```sh
PAYCORE_INBOX_ENABLED=true KAFKA_BOOTSTRAP_SERVERS=localhost:9092 make run
```

## 5. Falhas

O listener não confirma o offset quando o handler lança exceção; o
`DefaultErrorHandler` tenta novamente com backoff fixo (1s, 3 tentativas) e
depois libera o registro. A dead-letter queue com política de retry e HMAC é o
próximo card; até lá, falhas permanentes exigem replay operacional.

## 6. Evidências

- `InboxIntegrationTest`: mesma entrega duas vezes, replay auditável, replay
  sem duplicar liquidação e replay negado para outro proprietário.
- `InboxConsumerIntegrationTest`: broker embutido, duas entregas idênticas no
  tópico versionado e apenas uma liquidação.
- Migração `V7__create_inbox_and_settlements.sql`.
