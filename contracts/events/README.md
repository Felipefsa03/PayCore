# Contratos de eventos

Contratos versionados dos eventos publicados no Kafka. Eles são a interface
assíncrona do PayCore: consumidores (parceiros, ledger, conciliação) dependem
destes documentos, não das classes internas do `financial-core`.

## Convenções

| Elemento          | Convenção                                              | Exemplo                                              |
| ----------------- | ------------------------------------------------------ | ---------------------------------------------------- |
| Arquivo           | `<evento>.v<versão>.schema.json`                       | `payment-completed.v1.schema.json`                   |
| `event_type`      | `<domínio>.<evento>`                                   | `payment.completed`                                  |
| `event_version`   | inteiro positivo, igual à versão do arquivo            | `1`                                                  |
| Tópico            | `paycore.<domínio>.<evento>.v<versão>`                 | `paycore.payments.payment-completed.v1`              |
| Chave de partição | `aggregate.id` (id do pagamento)                       | `9f1c…`                                              |
| Headers           | `event_id`, `event_type`, `event_version`, `correlation_id`, `content_type` | —                          |

O versionamento aparece **no nome do tópico e no `event_version`**. Um consumidor
inscrito em `…v1` nunca recebe um payload incompatível: mudanças incompatíveis
exigem um novo arquivo (`v2`) e um novo tópico.

## Compatibilidade

Os schemas são **estritos** (`additionalProperties: false`). A regra é:

1. **Correção** (descrição, exemplo, formatação): não muda o contrato, pode ser
   feita na mesma versão.
2. **Adição de campo opcional**: tecnicamente compatível para consumidores que
   ignoram campos desconhecidos, mas nossos schemas são estritos; por isso a
   adição também exige `v2` e um novo tópico.
3. **Remoção, renomeação ou mudança de tipo/semântica**: sempre `v2` e novo
   tópico. `v1` permanece publicado durante a janela de migração.

Essa política evita acoplamento oculto: um deploy do produtor não quebra um
consumidor antigo.

## Catálogo

| Evento              | Arquivo                              | Tópico                                          | Quando é publicado                          |
| ------------------- | ------------------------------------ | ----------------------------------------------- | ------------------------------------------- |
| `payment.completed` | `payment-completed.v1.schema.json`   | `paycore.payments.payment-completed.v1`         | Transferência confirmada com lançamento contábil. |
| `payment.rejected`  | `payment-rejected.v1.schema.json`    | `paycore.payments.payment-rejected.v1`          | Rejeição por regra de negócio (ex.: saldo insuficiente). |

## Semântica de entrega

A publicação é **at-least-once**: o outbox só marca o evento como publicado
depois que o broker confirma o envio. Uma queda entre o envio e o commit pode
reenviar o mesmo `event_id`. Consumidores devem deduplicar por `event_id`
(inbox) e tratar o processamento como idempotente.

## Validação

Os contratos são validados no build: o teste de contrato lê cada arquivo JSON
Schema, verifica `$id`, `event_type`, `event_version` e valida um envelope real
gerado pelo publicador. Rode `make test` (ou
`./mvnw -Dtest=EventContractTest test` no módulo `financial-core`).
