# Matriz de evidências por camada

| Campo | Valor |
| ----- | ----- |
| Status | Vigente |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-09 |
| Depende de | [Estratégia de testes](estrategia-de-testes.md), [Requisitos e invariantes](../requirements/requisitos-invariantes-glossario.md), [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md) |
| Missão | M1–M3 |

Legenda de status: **Implementado** = teste existe e roda no gate; **Parcial** = existe, mas não cobre todo o requisito; **Planejado** = requisito de milestone futura, sem teste ainda.

## 1. Resumo por camada

| Camada | Comando | Evidência publicada | Cobertura (linhas) | Status |
| ------ | ------- | ------------------- | ------------------ | ------ |
| Unitário | `make test-unit` | Surefire `shared`, `accounts`, `app` (`MoneyTest`, `AccountServiceTest`, `OwnerIdValidatorTest`) | Shared 50,0%; Accounts 93,0% | Implementado |
| Integração | `make test-integration` | Surefire `app` (`AccountApiIntegrationTest`, `FinancialCoreApplicationTest`) | App 83,5% | Implementado |
| Gate completo | `make test` | Artefato CI `financial-core-reports` (Surefire + JaCoCo + Checkstyle) | Total 81,2% | Implementado |
| Contrato | `npx @redocly/cli lint contracts/openapi/financial-core.yaml` | Lint registrado em `modelo-dominio-estados-transferencia.md` §4; tipos em `contracts/openapi/generated/financial-core.d.ts` | n/a | Parcial (lint manual, não automatizado no CI) |
| Estático | `make lint` | `checkstyle-result.xml` por módulo | n/a | Implementado (0 violações) |
| Documentação | `make docs-check` | Saída do checker em CI | n/a | Implementado |
| Segurança documental | `make security-check` | Saída do checker em CI | n/a | Implementado |
| E2E | `make up && make run` + cenário | n/a | n/a | Planejado (M2) |
| Performance | cenário k6 | n/a | n/a | Planejado (M2) |
| Resiliência | cenário de falha | n/a | n/a | Planejado (M6) |

## 2. Testes implementados

### 2.1 Unitários

| Teste | Arquivo | Regra provada | IDs | Status |
| ----- | ------- | ------------- | --- | ------ |
| `createsValidMoney` | `shared/.../MoneyTest.java` | `Money` válido em centavos com moeda ISO | RF-03 (base), I-02 | Implementado |
| `rejectsZeroOrNegativeAmount` | `shared/.../MoneyTest.java` | Valor não positivo é rejeitado | I-02, I-05 | Implementado |
| `rejectsInvalidCurrencyCode` | `shared/.../MoneyTest.java` | Código de moeda inválido é rejeitado | — | Implementado |
| `comparesCurrencyAcrossInstances` | `shared/.../MoneyTest.java` | Igualdade baseada em valor e moeda | — | Implementado |
| `rejectsCurrencyThatIsNotUppercaseIso4217` | `accounts/.../AccountServiceTest.java` | Moeda deve ser ISO-4217 maiúscula | RF-01 | Implementado |
| `createsAccountWithOwnerFromTheCaller` | `accounts/.../AccountServiceTest.java` | Conta nasce com proprietário do chamador e saldo zero | RF-01, I-08 | Implementado |
| `returnsAccountForItsOwner` | `accounts/.../AccountServiceTest.java` | Leitura do próprio proprietário funciona | RNF-06 | Implementado |
| `hidesAccountsOfOtherOwnersBehindAccessDenied` | `accounts/.../AccountServiceTest.java` | Conta de outro proprietário fica inacessível | RNF-06, T-02 | Implementado |
| `reportsMissingAccount` | `accounts/.../AccountServiceTest.java` | Conta inexistente retorna `not_found` | — | Implementado |
| `acceptsUuidSubject` | `app/.../OwnerIdValidatorTest.java` | `sub` JWT válido é aceito | RF-01, T-01, T-18 | Implementado |
| `rejectsSubjectThatIsNotUuid` | `app/.../OwnerIdValidatorTest.java` | `sub` não-UUID é rejeitado | T-01, T-18 | Implementado |
| `rejectsMissingSubject` | `app/.../OwnerIdValidatorTest.java` | Ausência de `sub` é rejeitada | T-01, T-18 | Implementado |

### 2.2 Integração

A Integration API em TypeScript tem sua própria suíte de integração em
`services/integration-api/test/integration`. Ela usa PostgreSQL real, um stub HTTP do
núcleo financeiro e um receptor de webhook que valida HMAC/timestamp, cobrindo: consulta da
própria transação, isolamento entre dois parceiros, validação de DTOs, rate limit,
correlação, segredo de webhook, contrato OpenAPI, entrega assinada, HTTP 500, timeout,
retry, DLQ, reenvio manual auditável, evento repetido e bloqueio de destinos privados.
Evidência: `npm run check` (23 testes) executada pelo workflow `integration-api-ci.yml`.

| Teste | Arquivo | Regra provada | IDs | Status |
| ----- | ------- | ------------- | --- | ------ |
| `rejectsRequestWithoutToken` | `app/.../AccountApiIntegrationTest.java` | Endpoint exige autenticação | RNF-06, T-01 | Implementado |
| `rejectsExpiredToken` | `app/.../AccountApiIntegrationTest.java` | Token expirado é rejeitado | T-01, T-18 | Implementado |
| `rejectsTokenWhoseSubjectIsNotAnOwnerUuid` | `app/.../AccountApiIntegrationTest.java` | `sub` inválido não autentica | T-01, T-18 | Implementado |
| `rejectsTokenSignedWithAnotherSecret` | `app/.../AccountApiIntegrationTest.java` | Assinatura inválida é rejeitada | T-01, T-18 | Implementado |
| `createsAccountOwnedByTheTokenSubject` | `app/.../AccountApiIntegrationTest.java` | `POST /accounts` cria conta com proprietário e saldo zero | RF-01, RF-02 | Implementado |
| `rejectsInvalidCurrencyWithStandardError` | `app/.../AccountApiIntegrationTest.java` | Moeda inválida retorna envelope estável | RF-01, T-12 | Implementado |
| `otherOwnerCannotReadTheAccount` | `app/.../AccountApiIntegrationTest.java` | Acesso cruzado retorna 403 | RF-02, RNF-06, T-02, T-13 | Implementado |
| `unknownAccountReturnsNotFound` | `app/.../AccountApiIntegrationTest.java` | Conta inexistente retorna 404 | — | Implementado |
| `computesBalanceFromPostingsAndPaginatesHistory` | `app/.../AccountApiIntegrationTest.java` | Saldo é projeção dos postings; histórico pagina | RF-02, I-01 (parcial) | Parcial |
| `rejectsInvalidCursor` | `app/.../AccountApiIntegrationTest.java` | Cursor opaco inválido é rejeitado | — | Implementado |
| `contextLoads` (x2) | `app/.../FinancialCoreApplicationTest.java` | Spring Boot sobe com migrations Flyway | — | Implementado |

## 3. Rastreabilidade de requisitos

### 3.1 Requisitos funcionais

| ID | Requisito | Camada/caso | Teste | Status |
| -- | --------- | ----------- | ----- | ------ |
| RF-01 | Criar contas | Integração/unitário | `createsAccountOwnedByTheTokenSubject`, `createsAccountWithOwnerFromTheCaller` | Implementado |
| RF-02 | Consultar saldo e histórico | Integração | `computesBalanceFromPostingsAndPaginatesHistory` | Implementado |
| RF-03 | Transferir valores | Integração | `completesTransferAndUpdatesBalances` | Implementado |
| RF-04 | Lançamentos balanceados | Integração | `completesTransferAndUpdatesBalances` (journal balanceado) | Implementado |
| RF-05 | Idempotência | Integração | `replaysSameTransferForSameKeyAndPayload`, `concurrentSameKeyCreatesSingleTransfer` | Implementado |
| RF-06 | Eventos de pagamento | Integração | `publishesCompletedEventToVersionedTopicKeyedByPayment`, `publishesRejectedEventToVersionedTopicKeyedByPayment` | Implementado |
| RF-07 | Consumidores idempotentes | Integração | `processesDeliveryOnceAndIgnoresTheDuplicate`, `duplicateDeliveriesCreateOnlyOneSettlement` | Implementado |
| RF-08 | Consultar transações/estados | Integração | `consulta a própria transação e propaga a correlação ao núcleo`; `completesTransferAndUpdatesBalances` | Implementado |
| RF-09 | Avaliar risco | n/a | — | Planejado (M4) |
| RF-10 | Conciliação | n/a | — | Planejado (M4) |
| RF-11 | Webhooks | Integração | `cadastra webhook devolvendo o segredo apenas na criação`, `entrega o envelope assinado, registra tentativa e expõe histórico`, `move para a DLQ, lista o dead-letter e reenvia manualmente com auditoria` | Implementado |
| RF-12 | Métricas e auditoria | n/a | — | Planejado (M5) |

### 3.2 Requisitos não funcionais

| ID | RNF | Evidência | Status |
| -- | --- | --------- | ------ |
| RNF-01 | Consistência | Planejado com ledger (M2) | Planejado |
| RNF-02 | Idempotência | Planejado com teste de concorrência (M2) | Planejado |
| RNF-03 | Integridade | `MoneyTest` valida valor > 0; constraints no schema; ledger no M2 | Parcial |
| RNF-04 | Recuperação | Planejado (M6) | Planejado |
| RNF-05 | Performance | Planejado (M2) | Planejado |
| RNF-06 | Segurança | `OwnerIdValidatorTest` + `AccountApiIntegrationTest` (401/403/404) | Implementado |
| RNF-07 | Observabilidade | `correlation_id` planejado; logs básicos | Parcial |

### 3.3 Invariantes

| ID | Invariante | Evidência | Status |
| -- | ---------- | --------- | ------ |
| I-01 | Partidas dobradas | `computesBalanceFromPostingsAndPaginatesHistory` valida leitura; balanceamento do ledger planejado M2 | Parcial |
| I-02 | Conservação de valor | `MoneyTest` valida valor positivo; transferências planejadas M2 | Parcial |
| I-03 | Imutabilidade | Append-only planejado M2 | Planejado |
| I-04 | Não-duplicação | Planejado M2 | Planejado |
| I-05 | Saldo não negativo | Planejado M2 | Planejado |
| I-06 | Durabilidade do confirmado | PostgreSQL + Flyway + Testcontainers; sem teste de falha | Parcial |
| I-07 | Falha ≠ execução | Planejado M2 | Planejado |
| I-08 | Auditabilidade | `created_at`, proprietário e IDs; histórico de estados planejado M2 | Parcial |

### 3.4 Ameaças do modelo de segurança

| Ameaça | Controle/teste | Status |
| ------- | -------------- | ------ |
| T-01 | JWT inválido/expirado/outro segredo rejeitado | Implementado |
| T-02 | Conta de outro proprietário inacessível | Implementado |
| T-12 | Envelope de erro sem detalhes internos | Implementado |
| T-13 | Histórico escopado por proprietário | Implementado |
| T-18 | Algoritmo/segredo JWT validados | Implementado |
| T-19 | JPA parametrizado; sem concatenação | Parcial (SAST no M5) |
| Demais T-03..T-24 | Controles planejados nas milestones indicadas | Planejado |

## 4. Lacunas com dono e prazo

Resumo da estratégia §7. A matriz de evidências só é considerada completa quando cada lacuna
abaixo for fechada ou explicitamente rejeitada com ADR.

| Lacuna | Dono | Quando |
| ------ | ---- | ------ |
| `TransferState` sem teste | Backend | M2 |
| Sem teste de concorrência/idempotência | Backend | M2 |
| Sem teste de ledger balanceado | Backend | M2 |
| Branch coverage < 70% | Backend/Qualidade | M2 |
| Contrato sem lint no CI | Backend | M2 |
| Sem e2e, performance e resiliência | Plataforma | M2/M6 |

## 5. Como atualizar esta matriz

1. Toda entrega que adiciona ou altera um teste atualiza a seção 2 e a seção 3.
2. A seção 1 é atualizada quando uma camada muda de status (ex.: contrato passa a rodar no CI).
3. Itens planejados só viram implementados com evidência publicada (Surefire/artefato).
4. Lacunas fechadas saem da seção 4 e entram na matriz com teste e evidência.

## Referências

- [Estratégia de testes](estrategia-de-testes.md)
- [Relatório de execução da M1](evidencias/M1-2026-11-06.md)
- [Requisitos, invariantes e glossário](../requirements/requisitos-invariantes-glossario.md)
- [Modelo de ameaças](../security/modelo-ameacas.md)
- [Padrão de documentação](../engineering/documentation-standard.md)
