# Modelo de domínio e máquina de estados da transferência

| Campo       | Valor                                                      |
| ----------- | ----------------------------------------------------------- |
| Status      | Em revisão                                                   |
| Autor       | Luis Felipe (com apoio de execução via Gartrol/Claude Code)  |
| Data        | 2026-11-05                                                   |
| Depende de  | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md), [Requisitos e glossário](../requirements/requisitos-invariantes-glossario.md) |
| Missão      | M1 · Fundação arquitetural (semana 2)                         |
| Contrato    | [`contracts/openapi/financial-core.yaml`](../../contracts/openapi/financial-core.yaml) |

## Objetivo

Detalhar o modelo de contas, transferências, lançamentos e chaves de idempotência já
introduzido no RFC-001 §5, formalizar a máquina de estados da transferência com suas
transições válidas, e especificar o contrato OpenAPI completo — incluindo casos de erro —
antes de qualquer implementação em código.

## 1. Modelo de contas, transferências, lançamentos e idempotência

### 1.1 Conta (`Account`)

| Campo            | Tipo                     | Regra                                                                 |
| ----------------- | ------------------------ | ------------------------------------------------------------------------ |
| `id`               | UUID                     | Identificador único, imutável.                                          |
| `ownerId`          | UUID                     | Proprietário; usado para autorização (RNF-06).                          |
| `currency`         | string (ISO 4217)        | Moeda da conta; transferências só ocorrem entre contas da mesma moeda.  |
| `overdraftPolicy`  | enum `forbidden`, `allowed` | Define se I-05 (saldo não negativo) é aplicada estritamente.         |
| `status`           | enum `active`, `closed`  | Conta `closed` não aceita novas transferências.                         |
| `createdAt`        | timestamp                | Imutável.                                                                |

Saldo **não é um campo armazenado diretamente na conta**: é a soma dos `postings`
efetivados da conta (RFC-001 §5, "Saldo é projeção"). Isso elimina uma classe de bugs onde
saldo e ledger divergem.

### 1.2 Transferência (`Transfer`)

| Campo              | Tipo        | Regra                                                                     |
| ------------------- | ----------- | ---------------------------------------------------------------------------- |
| `id`                 | UUID        | Identificador único da operação lógica.                                     |
| `sourceAccountId`    | UUID        | Conta de origem.                                                             |
| `destinationAccountId` | UUID     | Conta de destino; deve ter a mesma `currency` da origem.                     |
| `amount`             | integer     | Valor em centavos; `amount > 0`.                                            |
| `currency`           | string      | Deve coincidir com a moeda de ambas as contas.                               |
| `idempotencyKey`     | string      | Ver §1.4.                                                                    |
| `correlationId`      | UUID        | Amarra a transferência a eventos e logs derivados (I-08).                    |
| `state`              | enum        | Ver §2.                                                                      |
| `failureReason`      | string?     | Preenchido apenas em `REJECTED`/`FAILED`.                                    |
| `journalEntryId`     | UUID?       | Preenchido apenas quando `state = COMPLETED`.                                |
| `createdAt`/`updatedAt` | timestamp | `updatedAt` muda a cada transição de estado.                               |

### 1.3 Lançamento e registro de diário (`JournalEntry` / `Posting`)

```
JournalEntry
├── id: UUID
├── transferId: UUID            (1:1 com a transferência que o originou)
├── currency: string
├── createdAt: timestamp         (imutável — nunca há updatedAt)
└── postings: Posting[]          (mínimo 2, soma = 0)

Posting
├── id: UUID
├── journalEntryId: UUID
├── accountId: UUID
├── direction: enum DEBIT | CREDIT
└── amount: integer (centavos, sempre positivo; o sinal vem de `direction`)
```

Invariante de persistência (reforça I-01 do RFC-001): um `JournalEntry` só pode ser
inserido dentro de uma transação que valida, antes do commit, que
`SUM(postings WHERE direction = CREDIT) = SUM(postings WHERE direction = DEBIT)` por
`currency`. Uma transferência simples gera exatamente 2 postings (débito na origem,
crédito no destino); lançamentos compensatórios (I-03) geram um novo `JournalEntry`
separado, nunca alteram o original.

### 1.4 Chave de idempotência (`IdempotencyRecord`)

| Campo         | Tipo      | Regra                                                                        |
| -------------- | --------- | -------------------------------------------------------------------------------- |
| `key`           | string    | Fornecida pelo cliente; única por `ownerId` (escopo por cliente/operação).      |
| `fingerprint`   | string    | Hash SHA-256 do payload relevante da requisição (RFC-001 glossário).            |
| `transferId`    | UUID?     | Preenchido quando a chave já produziu uma transferência.                       |
| `responseSnapshot` | JSON  | Resposta original, devolvida em repetições legítimas sem reprocessar.           |
| `createdAt`     | timestamp | Usado para expiração da chave (política de retenção a definir operacionalmente). |

Regra de decisão (implementa RF-05 e I-04):

1. Chave inédita → cria `IdempotencyRecord`, processa normalmente.
2. Chave existente + mesmo `fingerprint` → devolve `responseSnapshot` sem reprocessar (repetição legítima).
3. Chave existente + `fingerprint` diferente → rejeita com `409 idempotency_key_conflict` (reuso indevido).
4. Chave existente + operação ainda em `PROCESSING` → rejeita com `409 idempotency_key_in_progress` (concorrência com a mesma chave).

## 2. Máquina de estados da transferência

```
                 ┌─────────────┐
   POST /transfers│             │
   ──────────────▶│  RECEIVED   │
                 └──────┬──────┘
                        │ validação de payload, contas e idempotência
                        ▼
                 ┌─────────────┐
                 │ VALIDATING  │──────────────┐
                 └──────┬──────┘              │ regra de negócio viola
                        │ aprovado             │ (saldo, moeda, conta fechada)
                        ▼                      ▼
                 ┌─────────────┐        ┌─────────────┐
                 │ PROCESSING  │        │  REJECTED   │ (terminal)
                 └──────┬──────┘        └─────────────┘
          efetivação ok │  │ erro inesperado após aceite
                        ▼  ▼
                 ┌─────────────┐        ┌─────────────┐
                 │  COMPLETED  │        │   FAILED    │ (terminal)
                 │  (terminal) │        └─────────────┘
                 └─────────────┘
```

### 2.1 Tabela de transições válidas

| De            | Para          | Gatilho                                                             | Efeito contábil |
| -------------- | -------------- | ---------------------------------------------------------------------- | ---------------- |
| —              | `RECEIVED`     | Requisição aceita e persistida com `idempotencyKey`                    | Nenhum            |
| `RECEIVED`     | `VALIDATING`   | Início da validação síncrona                                           | Nenhum            |
| `VALIDATING`   | `REJECTED`     | Conta inexistente/fechada, moeda incompatível, ou saldo insuficiente (I-05) | Nenhum        |
| `VALIDATING`   | `PROCESSING`   | Validações de negócio aprovadas                                        | Nenhum            |
| `PROCESSING`   | `COMPLETED`    | `JournalEntry` balanceado efetivado com sucesso (I-01, I-02)            | Débito + crédito  |
| `PROCESSING`   | `FAILED`       | Erro de infraestrutura durante a efetivação (ex.: timeout de banco)    | Nenhum (tudo-ou-nada) |

### 2.2 Propriedades da máquina

- **`REJECTED` e `FAILED` são terminais e mutuamente exclusivos de `COMPLETED`.** Nenhuma
  transferência tem efeito contábil parcial (reforça I-02).
- **Nenhuma transição remove ou substitui `COMPLETED`.** Uma correção é uma nova
  transferência compensatória com seu próprio ciclo de estados (reforça I-03).
- **`FAILED` é diferente de `REJECTED`**: `REJECTED` é uma decisão de negócio (antes de
  qualquer tentativa de efetivação); `FAILED` é uma falha técnica após o aceite, sem efeito
  parcial. Essa distinção implementa a invariante I-07 — o cliente sempre pode diferenciar
  "sua solicitação foi recusada por regra de negócio" de "o sistema falhou processando".
- **Toda transição é registrada com timestamp e motivo**, formando o histórico consultado
  por RF-08 e pela invariante de auditabilidade I-08.
- Por ora o diagrama cobre apenas o caminho do núcleo contábil local (M1–M2). Os efeitos de
  publicação de evento (`PaymentCompleted`/`PaymentRejected`, RF-06) e as integrações
  dependentes de risco (RF-09, M4) serão modelados como consumidores do estado `COMPLETED`/
  `REJECTED`, não como novos estados da transferência.

## 3. Casos de erro padronizados

Todos os erros da API seguem um envelope único (ver `contracts/openapi/financial-core.yaml`,
schema `Error`), com um `code` estável e estável entre versões:

| HTTP | `code`                          | Quando ocorre                                                       |
| ---- | -------------------------------- | ------------------------------------------------------------------- |
| 400  | `validation_error`               | Payload malformado (campo ausente, tipo inválido, `amount` ≤ 0)     |
| 401  | `unauthenticated`                | Credencial ausente ou inválida                                      |
| 403  | `forbidden`                      | Cliente autenticado tenta acessar conta de outro proprietário       |
| 404  | `account_not_found`              | `sourceAccountId` ou `destinationAccountId` não existe               |
| 409  | `insufficient_funds`             | Transferência violaria I-05 (saldo ficaria negativo sem permissão)   |
| 409  | `currency_mismatch`              | Moedas de origem e destino diferem                                   |
| 409  | `account_closed`                 | Conta de origem ou destino está `closed`                             |
| 409  | `idempotency_key_conflict`       | Mesma chave, `fingerprint` diferente (§1.4, caso 3)                   |
| 409  | `idempotency_key_in_progress`    | Mesma chave, operação ainda `PROCESSING` (§1.4, caso 4)                |
| 422  | `self_transfer_not_allowed`      | `sourceAccountId == destinationAccountId`                            |
| 500  | `internal_error`                 | Falha inesperada; nunca expõe detalhes internos no corpo da resposta |

## 4. Validação do contrato (passo de confirmação do card)

O contrato foi validado com lint e um cliente de teste foi gerado a partir dele:

```sh
npx @redocly/cli lint contracts/openapi/financial-core.yaml
# → 0 erros, 1 aviso aceitável (servidor localhost, justificado na descrição do server)

npx openapi-typescript contracts/openapi/financial-core.yaml \
  -o contracts/openapi/generated/financial-core.d.ts
# → tipos TypeScript gerados para todos os paths e schemas (paths, components, operations)
```

O arquivo gerado (`contracts/openapi/generated/financial-core.d.ts`) é descartável e
reprodutível a qualquer momento a partir do contrato; não deve ser editado manualmente.
Isso prova que o contrato é sintaticamente válido e consumível por um gerador de cliente
real, antes de qualquer implementação em Java.

## Referências

- [RFC-001 — PayCore: Visão e Arquitetura Inicial](../rfc/RFC-001-visao-e-arquitetura-inicial.md)
- [Requisitos rastreáveis, invariantes e glossário](../requirements/requisitos-invariantes-glossario.md)
- [`contracts/openapi/financial-core.yaml`](../../contracts/openapi/financial-core.yaml)
