# Kafka, tópicos versionados e contratos de eventos

| Campo      | Valor                                                                                     |
| ---------- | ----------------------------------------------------------------------------------------- |
| Status     | Implementado                                                                              |
| Autor      | Luis Felipe (com apoio de execução via Gartrol/OpenCode)                                  |
| Data       | 2026-11-07                                                                                |
| Depende de | [Transactional Outbox](transactional-outbox.md), [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md), [Requisitos e glossário](../requirements/requisitos-invariantes-glossario.md) |
| Missão     | M3 · Mensageria e integrações (semanas 9–12)                                              |

## Objetivo

Publicar os eventos de pagamento em um broker Kafka local, com tópicos
versionados, contratos explícitos em `contracts/events` e particionamento por
chave que preserve a ordem por pagamento. O outbox continua sendo a fonte da
verdade: o dispatcher só marca `PUBLISHED` depois que o broker confirma o envio.

## 1. Visão geral

```text
TransferService ──(mesma transação)──▶ outbox_events (PENDING)
                                            │
                              OutboxDispatcher (SKIP LOCKED)
                                            │
                              KafkaDomainEventPublisher
                                            │
              ┌─────────────────────────────┴─────────────────────────────┐
              ▼                                                           ▼
 paycore.payments.payment-completed.v1              paycore.payments.payment-rejected.v1
              │                                                           │
       consumidor idempotente (card seguinte)                     consumidor idempotente
```

## 2. Tópicos e versionamento

| Evento interno      | Contrato publicado   | Tópico                                          | Partições |
| ------------------- | -------------------- | ----------------------------------------------- | --------- |
| `transfer.completed`| `payment.completed`  | `paycore.payments.payment-completed.v1`         | 3         |
| `transfer.rejected` | `payment.rejected`   | `paycore.payments.payment-rejected.v1`          | 3         |

O nome do tópico carrega a versão do contrato. Um consumidor inscrito em `v1`
não recebe um payload incompatível: mudanças incompatíveis geram `v2` e um novo
tópico, mantendo `v1` durante a janela de migração. A regra de compatibilidade
está em [`contracts/events/README.md`](../../contracts/events/README.md).

## 3. Partição e ordem

A chave da mensagem é `aggregate.id` — o id do pagamento (transferência). Com
isso, todos os eventos de um mesmo pagamento caem na mesma partição e são
consumidos na ordem de produção por partição. Pagamentos diferentes podem ser
processados em paralelo.

O outbox publica eventos de um mesmo agregado em ordem de `created_at`; como o
dispatcher respeita `ORDER BY available_at, created_at` e usa a mesma chave, a
ordem é preservada por pagamento.

## 4. Envelope

Todo evento tem o mesmo envelope; o payload de domínio fica em `data`:

```json
{
  "event_id": "9864f1e2-…",
  "event_type": "payment.completed",
  "event_version": 1,
  "occurred_at": "2026-11-07T01:20:00Z",
  "correlation_id": "2e4f…",
  "aggregate": { "type": "payment", "id": "9f1c…" },
  "data": {
    "transferId": "9f1c…",
    "state": "COMPLETED",
    "sourceAccountId": "…",
    "destinationAccountId": "…",
    "amount": 250,
    "currency": "BRL",
    "correlationId": "2e4f…",
    "journalEntryId": "…"
  }
}
```

Headers Kafka: `event_id`, `event_type`, `event_version`, `correlation_id`,
`content_type: application/json`. O `event_id` é o id da linha do outbox e é a
chave de deduplicação do consumidor.

## 5. Configuração

O transporte é selecionado por `paycore.outbox.transport`:

| Valor   | Efeito                                                              |
| ------- | ------------------------------------------------------------------- |
| `log`   | Transporte de laboratório (padrão); não requer broker.               |
| `kafka` | Publica nos tópicos versionados; requer `spring.kafka.bootstrap-servers`. |

Producer: `acks=all`, `enable.idempotence=true`, compressão `lz4` e até 10
retries. O dispatcher aguarda o `ack` do broker com timeout configurável
(`paycore.outbox.kafka-send-timeout-ms`); se o broker não confirmar, o evento
permanece `PENDING` e entra no retry exponencial do outbox.

## 6. Kafka local

O `docker compose` do repositório sobe um único broker em modo KRaft:

```sh
make up
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 PAYCORE_OUTBOX_TRANSPORT=kafka make run
```

Os tópicos são declarados pela aplicação (`KafkaOutboxConfiguration`) com 3
partições e 1 réplica; a criação automática de tópicos fica desabilitada no
broker. Para inspecionar:

```sh
docker compose -f infrastructure/docker/docker-compose.yml \
  exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Sem Docker, os testes usam o broker embutido do `spring-kafka-test`
(`@EmbeddedKafka`), sem depender do serviço local.

## 7. Evidências

- `EventContractTest`: mapping interno → contrato, envelope e validação dos dois
  JSON Schemas com o validador `networknt`.
- `KafkaOutboxIntegrationTest`: sobe um broker embutido com 3 partições,
  executa a transferência, dispara o dispatcher e consome a mensagem,
  verificando tópico versionado, chave = id do pagamento, headers, envelope e
  schema. Cobre `payment.completed` e `payment.rejected`.
- Contratos estritos em [`contracts/events`](../../contracts/events/README.md).

## 8. Limitações e próximos passos

- O consumidor idempotente com inbox e replay é o próximo card; até lá a
  garantia é at-least-once e a deduplicação é responsabilidade do consumidor.
- O broker local é single-node e não representa réplica, retenção ou disaster
  recovery de produção.
- A senha/TLS do Kafka não é configurada no laboratório; em ambientes
  compartilhados use SASL/TLS e segredos fora do repositório.
