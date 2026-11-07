# Transactional Outbox na transação do pagamento

| Campo       | Valor                                                                                        |
| ----------- | -------------------------------------------------------------------------------------------- |
| Status      | Implementado                                                                                  |
| Autor       | Luis Felipe (com apoio de execução via Gartrol/OpenCode)                                      |
| Data        | 2026-11-07                                                                                    |
| Depende de  | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md), [Requisitos e glossário](../requirements/requisitos-invariantes-glossario.md), [Modelo de domínio](modelo-dominio-estados-transferencia.md) |
| Missão      | M3 · Mensageria e integrações (semanas 9–12)                                                  |
| Requisito   | RF-06                                                                                         |

## Objetivo

Garantir que a confirmação de um pagamento e o registro do evento de domínio
correspondente sejam atômicos. Sem outbox, o commit no banco e a publicação no
broker podem divergir: o pagamento fica confirmado sem evento ou o evento é
publicado para um pagamento que não existe.

O padrão adotado é o **transactional outbox**: a aplicação grava o evento em uma
tabela do mesmo banco, na mesma transação da mudança de estado. Um publicador
separado lê as linhas pendentes e as envia ao broker, com retry.

## 1. Tabela `outbox_events`

Migração: [`V6__create_outbox_events.sql`](../../services/financial-core/app/src/main/resources/db/migration/V6__create_outbox_events.sql).

| Coluna           | Tipo          | Regra                                                              |
| ---------------- | ------------- | ------------------------------------------------------------------ |
| `id`             | UUID          | Identificador do evento.                                           |
| `aggregate_type` | varchar(64)   | Tipo do agregado, por exemplo `transfer`.                          |
| `aggregate_id`   | UUID          | Identificador do agregado que gerou o evento.                      |
| `event_type`     | varchar(120)  | Nome versionado do evento, por exemplo `transfer.completed`.       |
| `event_version`  | integer       | Versão do contrato do evento; começa em `1`.                       |
| `payload`        | jsonb         | Corpo do evento, sem dados sensíveis.                              |
| `correlation_id` | UUID          | Correlação ponta a ponta com a requisição de origem.               |
| `status`         | varchar(16)   | `PENDING`, `PUBLISHED` ou `FAILED`.                                |
| `attempts`       | integer       | Tentativas de publicação já realizadas.                            |
| `available_at`   | timestamptz   | Próximo instante elegível para publicação (base do backoff).       |
| `published_at`   | timestamptz   | Preenchido apenas quando `status = PUBLISHED`.                     |
| `last_error`     | text          | Último erro do publicador, limitado a 2.000 caracteres.            |
| `created_at`     | timestamptz   | Instante de criação.                                               |

Restrições relevantes:

- `CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))` impede um evento
  publicado sem data e uma data sem status publicado.
- Índice parcial `idx_outbox_due (available_at) WHERE status = 'PENDING'` mantém
  a varredura do publicador proporcional ao backlog, não ao histórico.

## 2. Escrita na mesma transação

`TransferService.transfer` já é `@Transactional`. Depois de confirmar a
transferência (`COMPLETED`) ou registrar uma rejeição de negócio (`REJECTED`),
ele chama `OutboxEventService.append`, que participa da transação corrente
(`Propagation.REQUIRED`). Consequências:

- se o pagamento falhar depois da gravação do evento, o evento também é revertido;
- se o evento não puder ser gravado, o pagamento não é confirmado;
- um replay idempotente que devolve a transferência existente **não** cria um
  segundo evento.

## 3. Publicação e controle de concorrência

`OutboxDispatcher.dispatchOnce` abre uma transação, reserva um lote com:

```sql
SELECT * FROM outbox_events
 WHERE status = 'PENDING' AND available_at <= now()
 ORDER BY available_at, created_at
 LIMIT :limit
 FOR UPDATE SKIP LOCKED
```

e publica cada evento pela porta `DomainEventPublisher`. `SKIP LOCKED` permite
várias instâncias do publicador ao mesmo tempo: cada uma trava um subconjunto
diferente e nenhuma publica a mesma linha concorrentemente. O lote é marcado e
confirmado na mesma transação.

O transporte padrão é `LoggingDomainEventPublisher`, que registra o evento no
log. A integração com Kafka é o próximo card do M3; a porta isola o resto do
código da escolha de transporte.

## 4. Retry e backoff

Falhas de publicação (broker indisponível, timeout) mantêm o evento `PENDING` e
incrementam `attempts`. A próxima tentativa é agendada com backoff exponencial e
teto:

```text
delay = min(retryMaxMs, retryBaseMs * 2^(attempt - 1))
```

Os valores são configuráveis em `paycore.outbox.*`:

| Propriedade                     | Padrão   | Efeito                                        |
| ------------------------------- | -------- | --------------------------------------------- |
| `paycore.outbox.batch-size`     | 50       | Eventos reservados por ciclo.                 |
| `paycore.outbox.poll-interval-ms` | 5000   | Intervalo entre ciclos do agendador.          |
| `paycore.outbox.retry-base-ms`  | 1000     | Base do backoff.                              |
| `paycore.outbox.retry-max-ms`   | 300000   | Teto do backoff.                              |
| `paycore.outbox.max-attempts`   | 12       | Tentativas antes de marcar `FAILED`.          |
| `paycore.outbox.scheduled`      | true     | Liga/desliga o agendador (testes usam `false`). |

## 5. Semântica at-least-once

A publicação externa acontece **antes** do commit que marca o evento como
`PUBLISHED`. Se o processo cair entre o envio ao broker e o commit, o evento
continua `PENDING` e será reenviado. Portanto:

- nenhum pagamento confirmado perde o evento;
- o mesmo evento pode ser entregue mais de uma vez;
- consumidores precisam ser idempotentes (inbox/deduplicação, card seguinte do
  M3), usando `id` do evento ou uma chave de negócio estável.

Essa é a troca explícita do padrão: prioriza-se não perder eventos, aceitando
duplicatas. Efeitos financeiros duplicados são impedidos na camada de consumo,
não no transporte.

## 6. Falha permanente

Ao atingir `max-attempts`, o evento passa a `FAILED` e preserva `last_error`.
Ele deixa de ser reivindicado pelo publicador, mas permanece no banco para
inspeção e reprocessamento manual. A dead-letter queue de webhooks é tratada em
card próprio; aqui o `FAILED` é o ponto de parada auditável do outbox.

## 7. Evidências

Testes em
[`OutboxIntegrationTest`](../../services/financial-core/app/src/test/java/com/paycore/financialcore/app/outbox/OutboxIntegrationTest.java)
cobrem:

- evento gravado na mesma transação da transferência concluída;
- evento de rejeição de negócio (`transfer.rejected`);
- rollback do pagamento remove o evento;
- broker indisponível mantém o evento pendente, incrementa tentativas e agenda
  retry; quando o broker volta, o evento é publicado;
- evento vira `FAILED` após o número máximo de tentativas;
- duas instâncias concorrentes não publicam a mesma linha (`SKIP LOCKED`).

Comando (sem Docker, usando PostgreSQL local):

```sh
PAYCORE_IT_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/paycore_it \
  make test-integration
```
