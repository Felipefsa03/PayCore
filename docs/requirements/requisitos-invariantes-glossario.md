# Requisitos rastreáveis, invariantes financeiras e glossário de domínio

| Campo       | Valor                                                      |
| ----------- | ----------------------------------------------------------- |
| Status      | Em revisão                                                   |
| Autor       | Luis Felipe (com apoio de execução via Gartrol/Claude Code)  |
| Data        | 2026-11-05                                                   |
| Depende de  | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md) (validado) |
| Missão      | M1 · Fundação arquitetural (semanas 1–4)                      |

## Objetivo

Tornar cada requisito do PayCore rastreável até um caso de uso e uma evidência de teste
futura, para que a implementação não precise inventar regras de negócio não documentadas.
Este documento detalha e complementa RF-01..RF-12 e as invariantes I-01..I-08 já
introduzidas no RFC-001, acrescentando a matriz requisito → caso de uso → evidência exigida
pelo card.

## 1. Matriz de rastreabilidade — requisitos funcionais

| ID    | Requisito                                           | Prioridade | Caso de uso                                                                 | Evidência futura (teste)                                                              |
| ----- | ---------------------------------------------------- | ---------- | ---------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| RF-01 | Criar contas financeiras simuladas                   | P0         | UC-01 Abrir conta — cliente autenticado cria uma conta em uma moeda suportada | Teste de integração: `POST /accounts` cria conta com saldo zero e proprietário correto |
| RF-02 | Consultar saldo disponível e histórico                | P0         | UC-02 Consultar conta — proprietário lê saldo e lançamentos de sua conta     | Teste de integração: saldo retornado é igual à soma dos postings efetivados             |
| RF-03 | Transferir valores entre contas                      | P0         | UC-03 Transferir — proprietário solicita movimento de valor entre duas contas internas | Teste de integração: transferência válida resulta em `COMPLETED` com lançamento balanceado |
| RF-04 | Garantir lançamentos contábeis balanceados            | P0         | UC-03 (efetivação) — nenhum journal entry é persistido sem débito = crédito | Teste unitário: tentativa de persistir entry desbalanceado é rejeitada (prova I-01)      |
| RF-05 | Evitar processamento duplicado por idempotência       | P0         | UC-04 Reenviar requisição — cliente repete a mesma chamada após timeout     | Teste de concorrência: 100 requisições simultâneas, mesma `Idempotency-Key`, 1 efetivação (prova I-04, C-02) |
| RF-06 | Publicar eventos de pagamento com segurança            | P0         | UC-05 Notificar conclusão — sistema publica evento após `COMPLETED`         | Teste de integração: evento só é publicado após commit da transação (Outbox, M3)         |
| RF-07 | Processar eventos com consumidores idempotentes        | P0         | UC-06 Consumir evento — consumidor reage sem duplicar efeito em replay      | Teste de integração: replay do mesmo evento não gera segundo efeito (prova I-04, M3)     |
| RF-08 | Consultar transações e seus estados                    | P0         | UC-07 Consultar transferência — proprietário acompanha estado e histórico  | Teste de integração: todas as transições de estado ficam visíveis com timestamp (prova I-08) |
| RF-09 | Avaliar risco e aplicar bloqueios ou revisão            | P1         | UC-08 Avaliar risco — motor decide `APPROVE`/`REVIEW`/`REJECT` antes de efetivar | Teste de integração: mesma entrada e versão de regra produz mesma decisão (M4)           |
| RF-10 | Conciliar transações com registros externos simulados  | P1         | UC-09 Conciliar — processo compara extrato importado com o ledger          | Teste de integração: divergências introduzidas intencionalmente são detectadas (M4)      |
| RF-11 | Emitir webhooks com assinatura e retry                  | P1         | UC-10 Notificar parceiro — webhook é entregue com HMAC e reenviado em falha | Teste de integração: timeout e HTTP 500 geram retry sem duplicar efeito no consumidor (M3) |
| RF-12 | Disponibilizar métricas operacionais e trilhas de auditoria | P1    | UC-11 Auditar — operador investiga uma transação por `trace_id`            | Teste manual/integração: toda operação é localizável por identificador de correlação (M5) |

Cobertura: todos os itens **P0** (RF-01..RF-08) têm caso de uso e evidência de teste
definidos sem lacunas, conforme exigido pelo passo de confirmação do card. Itens **P1**
(RF-09..RF-12) têm caso de uso e evidência associados a milestones futuras (M3–M5), a
detalhar nos cards correspondentes.

## 2. Requisitos não funcionais mensuráveis

| ID     | RNF              | Meta mensurável                                                                                      | Como medir                                                                 |
| ------ | ---------------- | ------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| RNF-01 | Consistência      | 0 transferências `COMPLETED` sem journal entry balanceado                                              | Auditoria automatizada: `SUM(postings.amount)` por entry = 0 por moeda      |
| RNF-02 | Idempotência      | 100 requisições simultâneas, mesma chave e payload → exatamente 1 efetivação                           | Teste de carga com chave fixa (k6/JUnit), contagem de journal entries criados |
| RNF-03 | Integridade       | 0 lançamentos isolados (sem par de contrapartida) aceitos                                              | Constraint de banco + teste unitário de rejeição                            |
| RNF-04 | Disponibilidade/Recuperação | 100% das operações confirmadas antes de uma falha simulada permanecem consultáveis após reinício | Experimento de resiliência: reinício do serviço durante processamento (M6)  |
| RNF-05 | Performance       | Baseline publicado; meta experimental p95 < 300 ms para transferência interna sob carga definida       | k6/Gatling com ambiente, volume e concorrência documentados (M2, semana 8)  |
| RNF-06 | Segurança         | 100% dos endpoints de conta exigem autenticação e autorização por proprietário                        | Teste de integração: acesso de proprietário diferente retorna 403/404       |
| RNF-07 | Observabilidade   | 100% das requisições carregam `trace_id`/`correlation_id` propagado entre componentes                  | Inspeção de logs estruturados e traces (OpenTelemetry, M5)                   |

Os valores acima são metas de laboratório (ambiente controlado, saldos fictícios), não
compromissos de produção — consistente com o RFC-001 §3.2.

## 3. Invariantes financeiras

As invariantes I-01 a I-08 foram definidas no [RFC-001 §4](../rfc/RFC-001-visao-e-arquitetura-inicial.md#4-invariantes-financeiras)
e são reproduzidas aqui como referência rápida, já que orientam a matriz de rastreabilidade
acima:

| #    | Invariante                                                                                         |
| ---- | ---------------------------------------------------------------------------------------------------- |
| I-01 | Partidas dobradas: todo lançamento efetivado soma zero por moeda e por transação.                     |
| I-02 | Conservação de valor: uma transferência concluída não cria nem destrói valor.                         |
| I-03 | Imutabilidade: lançamentos efetivados nunca são alterados; correções são compensatórias.              |
| I-04 | Não-duplicação: mesma `Idempotency-Key` + fingerprint produz no máximo uma movimentação.               |
| I-05 | Saldo não negativo quando a política da conta proíbe descoberto.                                      |
| I-06 | Durabilidade do confirmado: operação confirmada permanece registrada após qualquer falha.             |
| I-07 | Falha de comunicação ≠ falha de execução: timeout não implica reversão automática.                    |
| I-08 | Auditabilidade: toda operação tem identificador único, histórico de estados e origem rastreável.      |

Qualquer requisito novo que viole uma destas invariantes deve ser rejeitado ou ter a
invariante revista explicitamente em uma revisão de arquitetura (ADR), nunca contornado
silenciosamente na implementação.

## 4. Glossário de domínio

| Termo                                   | Definição                                                                                                             |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| **Conta**                                | Registro que acumula lançamentos e expõe saldo disponível; pertence a um único proprietário (cliente).                  |
| **Transferência**                        | Operação lógica que move valor entre duas contas internas; tem identidade própria e ciclo de vida de estados.           |
| **Saldo**                                | Projeção derivada da soma dos lançamentos efetivados de uma conta; nunca é persistido como fonte primária da verdade.    |
| **Lançamento (posting)**                 | Débito ou crédito individual, em centavos, associado a uma conta e a um registro de diário.                            |
| **Registro de diário (journal entry)**   | Conjunto atômico e balanceado de lançamentos (débitos = créditos) que materializa uma transação contábil.               |
| **Liquidação**                           | Momento em que uma transferência produz efeitos contábeis definitivos e imutáveis no ledger (estado `COMPLETED`).       |
| **Chave de idempotência**                | Identificador fornecido pelo cliente que, combinado ao fingerprint do payload, deduplica requisições equivalentes.      |
| **Fingerprint**                          | Hash determinístico do conteúdo relevante da requisição, usado para distinguir repetição legítima de reuso indevido de chave. |
| **Descoberto (overdraft)**               | Situação em que o saldo de uma conta ficaria negativo; permitida ou proibida por política explícita da conta.           |
| **Lançamento compensatório**             | Novo registro de diário que reverte o efeito de um lançamento anterior sem alterá-lo (preserva I-03).                   |
| **Correlação (`correlation_id`)**        | Identificador que amarra todas as operações e eventos derivados de uma mesma solicitação de negócio, para auditoria.    |
| **Trace (`trace_id`)**                   | Identificador de rastreamento distribuído (OpenTelemetry) que acompanha uma requisição através dos componentes técnicos. |

## 5. Rastreabilidade de aceite

Este documento satisfaz o passo de confirmação do card: cada requisito **P0** (RF-01..RF-08)
está mapeado a exatamente um caso de uso e a uma evidência de teste futura, sem lacunas
(ver §1). Requisitos **P1** têm caso de uso e evidência vinculados às milestones onde serão
implementados.

## Referências

- [RFC-001 — PayCore: Visão e Arquitetura Inicial](../rfc/RFC-001-visao-e-arquitetura-inicial.md)
- [Architecture Challenge #01 — Diagnóstico Inicial](../case-studies/architecture-challenge-01-diagnostico-inicial.md)
