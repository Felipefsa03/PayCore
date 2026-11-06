# RFC-001 — PayCore: Visão e Arquitetura Inicial

| Campo             | Valor                                                        |
| ----------------- | ------------------------------------------------------------ |
| Status            | Em revisão                                                    |
| Autor             | Luis Felipe (com apoio de execução via Gartrol/Claude Code)   |
| Data              | 2026-11-05                                                    |
| Decisões vinculadas | ADR-001 (monólito modular, planejado), [ADR-002](../adr/ADR-002-persistencia-postgresql.md) (persistência, aceito) |
| Missão            | M1 · Fundação arquitetural (semanas 1–4)                      |

---

## 1. Contexto do problema e objetivos

O PayCore é uma plataforma financeira B2B simulada que permite a empresas criar contas,
realizar transferências entre elas e acompanhar o resultado dessas operações de forma
segura, confiável e rastreável — sem precisar construir essa infraestrutura internamente.

O valor central do produto **não é mover saldo**: é permitir que o cliente **confie no
resultado** de cada operação. Se um cliente corporativo solicita uma transferência e ocorre
uma falha de comunicação, ele precisa conseguir descobrir se a operação foi efetivada,
rejeitada ou continua em processamento. Sem essa garantia, o cliente tende a repetir a
requisição, gerando movimentações duplicadas e inconsistências financeiras.

O problema central, portanto, decompõe-se em três necessidades:

1. **Integridade das movimentações** — débitos e créditos corretos, atômicos e conservativos.
2. **Rastreabilidade das operações** — todo movimento tem identidade, histórico de estados e trilha de auditoria.
3. **Simplicidade de integração** — um cliente consegue integrar-se usando apenas a documentação pública.

### Objetivos do projeto

- Demonstrar capacidade de **projetar, implementar, testar, operar e evoluir** um sistema
  distribuído, justificando decisões de arquitetura e medindo comportamento sob falhas.
- Validar as capacidades de integridade, rastreabilidade e integração em um **ambiente
  fechado com saldos fictícios** (MVP de laboratório), sem dinheiro real.
- Construir uma base que possa evoluir (extração de serviços, novas tecnologias) **sem
  comprometer as invariantes do domínio financeiro**.

### Premissa arquitetural

> A confiabilidade das operações financeiras deve ser estabelecida **antes** de qualquer
> investimento em mecanismos avançados de escalabilidade. Uma plataforma que processa
> milhares de operações incorretamente não é melhor do que uma que processa centenas
> corretamente.

### Parâmetros do projeto

- Duração: 24 semanas (≈ 288–360 h, 12–15 h semanais).
- Equipe simulada de referência: 3 engenheiros, 4 meses para o MVP.
- Tecnologias-alvo: Java, Python, PHP e TypeScript — cada uma **onde faz sentido**, não
  como vitrine de frameworks.

## 2. Escopo e funcionalidades excluídas

### Dentro do escopo (MVP — Milestones 1 e 2)

- Instituição financeira **simulada**, com contas fictícias e ambiente fechado.
- Criação e consulta de contas, com autenticação e autorização por proprietário.
- Transferências entre contas internas da plataforma, com estados explícitos.
- Ledger contábil de partidas dobradas (valores inteiros em centavos).
- Idempotência por `Idempotency-Key` com fingerprint de payload.
- Consulta de transações, estados e histórico.

### Dentro do escopo (fases seguintes, fora do MVP)

- Publicação de eventos de domínio via Transactional Outbox + Kafka (M3).
- API de integração para parceiros e webhooks assinados com retry (M3).
- Motor de risco com regras determinísticas e decisões `APPROVE`/`REVIEW`/`REJECT` (M4).
- Conciliação com extratos externos simulados e relatório de divergências (M4).
- Observabilidade, IaC, CI/CD com segurança, SLOs e runbooks (M5).
- Chaos engineering, análise de escalabilidade/custos e release de portfólio (M6).

### Explicitamente excluído

- **Movimentação de dinheiro real**, integração com instituições financeiras reais,
  arranjos de pagamento (Pix, cartões, TED) e obrigações regulatórias (BACEN, PCI-DSS).
- Multi-moeda com câmbio (o ledger registra moeda, mas não há conversão).
- Microsserviços desde o início — a extração de serviços só ocorre com necessidade
  técnica demonstrável (ver §6 e §7).
- Interfaces de usuário finais; o produto é API-first.
- Garantias de produção real: metas de desempenho e disponibilidade são de laboratório.

## 3. Requisitos

### 3.1 Requisitos funcionais

| ID    | Requisito                                                   | Prioridade |
| ----- | ----------------------------------------------------------- | ---------- |
| RF-01 | Criar contas financeiras simuladas                          | P0         |
| RF-02 | Consultar saldo disponível e histórico                      | P0         |
| RF-03 | Transferir valores entre contas                             | P0         |
| RF-04 | Garantir lançamentos contábeis balanceados                  | P0         |
| RF-05 | Evitar processamento duplicado por idempotência             | P0         |
| RF-06 | Publicar eventos de pagamento com segurança                 | P0         |
| RF-07 | Processar eventos com consumidores idempotentes             | P0         |
| RF-08 | Consultar transações e seus estados                         | P0         |
| RF-09 | Avaliar risco e aplicar bloqueios ou revisão                | P1         |
| RF-10 | Conciliar transações com registros externos simulados       | P1         |
| RF-11 | Emitir webhooks com assinatura e retry                      | P1         |
| RF-12 | Disponibilizar métricas operacionais e trilhas de auditoria | P1         |

### 3.2 Requisitos não funcionais

Metas de laboratório, não promessas de desempenho de produção.

- **Consistência** — nenhuma transferência concluída sem débito e crédito contábil
  correspondentes. Distinguimos consistência dos registros financeiros (forte, sempre)
  da consistência das projeções exibidas ao usuário (pode ser eventual, com estado
  explícito).
- **Idempotência** — repetição da mesma requisição com a mesma chave e payload não
  duplica a operação; mesma chave com payload diferente é rejeitada como conflito.
- **Integridade** — nenhum lançamento isolado quebra a igualdade entre débitos e créditos
  por moeda e por transação.
- **Disponibilidade/recuperação** — o sistema se recupera de interrupções simuladas sem
  perder operações já confirmadas; operações com resultado desconhecido são identificáveis.
- **Desempenho** — estabelecer baseline e buscar p95 < 300 ms para transferências internas
  no ambiente de teste. O número só é válido acompanhado de configuração do ambiente,
  volume de dados, concorrência, taxa de erros e metodologia de medição.
- **Segurança** — autenticação, autorização por conta (um cliente nunca lê conta de outro),
  validação de entradas e segredos fora do repositório.
- **Observabilidade** — logs estruturados, métricas, traces e identificadores de correlação
  (`trace_id`, `correlation_id`) propagados entre componentes.

## 4. Invariantes financeiras

Invariantes são propriedades que **nunca** podem ser violadas, em nenhum estado do sistema,
inclusive durante falhas parciais, concorrência e reprocessamento:

| #    | Invariante                                                                                       |
| ---- | ------------------------------------------------------------------------------------------------ |
| I-01 | **Partidas dobradas**: todo lançamento efetivado tem débitos e créditos que somam zero por moeda e por transação. |
| I-02 | **Conservação de valor**: uma transferência concluída não cria nem destrói valor; o total do sistema é constante. |
| I-03 | **Imutabilidade**: lançamentos efetivados nunca são alterados ou apagados; correções são lançamentos compensatórios. |
| I-04 | **Não-duplicação**: uma mesma operação lógica (mesma `Idempotency-Key` + fingerprint) produz no máximo uma movimentação financeira. |
| I-05 | **Saldo não negativo**: quando a política da conta proíbe descoberto, transferências concorrentes jamais levam o saldo disponível abaixo de zero. |
| I-06 | **Durabilidade do confirmado**: operação confirmada permanece registrada, consultável e auditável após qualquer reinício ou falha. |
| I-07 | **Falha de comunicação ≠ falha de execução**: timeout ou erro de rede nunca é interpretado automaticamente como operação não executada; o estado real é consultável. |
| I-08 | **Auditabilidade**: toda operação tem identificador único, histórico de transições de estado e origem rastreável. |

Cenários críticos que as invariantes protegem (base para os testes de aceitação do MVP):

- Conta com R$ 1.000 recebe duas transferências simultâneas de R$ 800 → apenas uma é
  aprovada (I-05); a outra é rejeitada por saldo insuficiente.
- Transferência confirmada no banco, resposta perdida na rede, cliente reenvia → nenhuma
  segunda movimentação (I-04, I-07); o cliente recebe o resultado original.
- Reprocessamento de eventos já consumidos → nenhum efeito financeiro duplicado (I-04).

## 5. Modelo de domínio inicial

### Glossário

| Termo                | Definição                                                                       |
| -------------------- | -------------------------------------------------------------------------------- |
| **Conta**            | Registro que acumula lançamentos e expõe saldo disponível; pertence a um cliente. |
| **Transferência**    | Operação lógica que move valor entre duas contas internas, com ciclo de vida próprio. |
| **Saldo**            | Projeção derivada dos lançamentos efetivados; nunca é fonte primária da verdade.  |
| **Lançamento (posting)** | Débito ou crédito individual, em centavos, vinculado a um registro de diário. |
| **Registro de diário (journal entry)** | Conjunto atômico e balanceado de lançamentos que materializa uma transação contábil. |
| **Chave de idempotência** | Identificador fornecido pelo cliente que, junto ao fingerprint do payload, deduplica operações. |
| **Liquidação**       | Momento em que a transferência produz efeitos contábeis definitivos no ledger.    |

### Entidades e relacionamentos

```
Cliente (1) ──── (N) Conta
Conta   (1) ──── (N) Posting
Transferência (1) ──── (1) JournalEntry (1) ──── (2..N) Posting
Transferência (0..1) ──── (1) IdempotencyRecord
```

- `accounts` — identidade, proprietário, moeda, política de descoberto, estado.
- `transfers` — origem, destino, valor, moeda, estado, `correlation_id`, timestamps por transição.
- `journal_entries` — agrupador atômico; só existe efetivado se balanceado (I-01).
- `postings` — débito/crédito em centavos; imutáveis após efetivação (I-03).
- `idempotency_records` — chave, fingerprint do payload, resultado persistido (I-04).

### Estados da transferência

```
RECEIVED ──► VALIDATING ──► PROCESSING ──► COMPLETED
                 │               │
                 ▼               ▼
             REJECTED         FAILED
```

- `RECEIVED` — requisição aceita e persistida com chave de idempotência.
- `VALIDATING` — validação de contas, moeda, política de saldo e (futuramente) risco.
- `PROCESSING` — efetivação contábil em andamento (janela de atomicidade).
- `COMPLETED` — journal entry efetivado; evento de domínio publicável.
- `REJECTED` — recusada antes de qualquer efeito contábil (ex.: saldo insuficiente).
- `FAILED` — falha após aceite, sem efeito contábil parcial (tudo-ou-nada).

Transições são registradas com timestamp e motivo; não há transição que remova um
`COMPLETED` (correção = transferência compensatória, I-03). O detalhamento fino de
estados e contratos OpenAPI é objeto do card "Modelo de domínio, estados da transferência
e contratos OpenAPI" (semana 2).

## 6. Alternativas arquiteturais consideradas

### A. Microsserviços desde o início (4 serviços poliglotas)

| Prós | Contras |
| ---- | ------- |
| Demonstra integração poliglota desde o dia 1; fronteiras físicas fortes | Consistência distribuída **no núcleo financeiro** (sagas/2PC) antes de o domínio estar estável |
| Escala independente por serviço | 3 pessoas × 4 meses: custo operacional (deploys, observabilidade, contratos) consome o orçamento do MVP |
| | Depuração de invariantes (I-01..I-08) através de fronteiras de rede é drasticamente mais difícil |

**Rejeitada para o MVP.** Introduz o modo de falha mais perigoso (inconsistência
distribuída) exatamente na parte mais sensível do sistema, sem necessidade demonstrada.

### B. Monólito único sem fronteiras internas

| Prós | Contras |
| ---- | ------- |
| Máxima simplicidade inicial; uma base de código, um deploy | Acoplamento cresce sem resistência; extração futura exige refatoração arriscada |
| Transações ACID locais triviais | Não exercita a competência-alvo (desenhar fronteiras de domínio) |

**Rejeitada.** Resolve o curto prazo, mas destrói a tese do projeto: evoluir para
distribuído **quando houver razão**, com fronteiras já ensaiadas.

### C. Monólito modular em Java + extração dirigida por necessidade (escolhida)

| Prós | Contras |
| ---- | ------- |
| Transações ACID locais protegem I-01..I-06 com o mecanismo mais simples possível | Exige disciplina para manter módulos desacoplados (mitigável com ArchUnit/revisão) |
| Fronteiras lógicas (`accounts`, `payments`, `ledger`, `shared`) ensaiam a extração futura | Um único runtime no início (aceitável no volume de laboratório) |
| Evolução incremental: Outbox/Kafka (M3) e serviços satélites (M4) entram **fora** do núcleo contábil | |

### D. Orientada a eventos desde o início (event sourcing no ledger)

| Prós | Contras |
| ---- | ------- |
| Trilha de auditoria nativa; replay natural | Curva de aprendizado e complexidade de projeções/versionamento de eventos no caminho crítico do MVP |
| | Partidas dobradas relacionais já fornecem auditabilidade exigida (I-03, I-08) com ferramentas maduras |

**Rejeitada para o núcleo.** A mensageria entra na M3 como **integração** (Outbox +
Kafka), não como fonte da verdade contábil.

## 7. Arquitetura escolhida e justificativa

**Monólito modular em Java/Spring Boot (`financial-core`), PostgreSQL como fonte única da
verdade contábil, evolução planejada para plataforma orientada a eventos com serviços
satélites poliglotas.**

### Visão de referência (alvo ao fim das 24 semanas)

| Tecnologia          | Componente                | Justificativa                                                                    |
| ------------------- | ------------------------- | -------------------------------------------------------------------------------- |
| Java + Spring Boot  | Payment Core e Ledger     | Transações, consistência, tipagem forte, concorrência e ecossistema corporativo  |
| Python + FastAPI    | Risk Engine               | Análise de dados, regras de risco e evolução futura para modelos estatísticos    |
| PHP + Laravel       | Reconciliation            | Rotinas de conciliação, importação de arquivos, agendamento e relatórios         |
| TypeScript + NestJS | Integration API           | Webhooks, contratos externos, integração com parceiros e APIs de borda           |
| PostgreSQL          | Persistência transacional | ACID, constraints, índices e controle de concorrência                            |
| Kafka               | Eventos de domínio        | Consumidores independentes, replay e processamento assíncrono                    |
| Redis               | Cache e rate limiting     | Dados temporários; **nunca** fonte da verdade financeira                         |

### Decisões estruturais

1. **Núcleo contábil local e ACID.** Contas, pagamentos e ledger vivem no mesmo processo e
   no mesmo banco. Débito, crédito, journal entry e registro de idempotência são efetivados
   em **uma transação**. É o mecanismo mais barato que satisfaz I-01..I-06. A
   [ADR-002](../adr/ADR-002-persistencia-postgresql.md) detalha bloqueio transacional vs.
   atualização condicional, ordem de aquisição de locks e idempotência.
2. **Módulos com fronteiras explícitas.** `accounts`, `payments`, `ledger`, `shared` —
   dependências permitidas verificadas em build. A fronteira lógica de hoje é o contrato de
   extração de amanhã. (ADR-001.)
3. **Monorepo** (`services/`, `contracts/`, `infrastructure/`, `tests/`, `docs/`) para
   facilitar revisão arquitetural, versionamento de contratos e execução do laboratório completo.
4. **Contratos antes de implementação.** APIs especificadas em OpenAPI na semana 2;
   eventos com schema versionado na M3.
5. **Extração somente com gatilho explícito**: requisito de escala medido, necessidade de
   isolamento de linguagem/time, ou acoplamento operacional comprovado — nunca "porque
   microsserviços". O ledger é o **último** candidato a extração; separar a contabilidade
   do processamento de pagamento introduziria consistência distribuída no ponto de maior risco.
6. **Serviços satélites entram pelas bordas.** Risk Engine, Reconciliation e Integration
   API consomem eventos/APIs do núcleo e falham de forma contida (timeout, circuit breaker,
   política explícita para indisponibilidade — ex.: pagamento sujeito a avaliação obrigatória
   **não** é aprovado silenciosamente quando o Risk Engine falha).

## 8. Riscos, trade-offs e critérios de sucesso

### 8.1 Maior risco

**Comprometer a integridade financeira durante o processamento de transferências, sob
concorrência, falhas parciais ou repetição de requisições.** Materializações típicas:

- Duas transferências simultâneas de R$ 800 numa conta com R$ 1.000 → dupla aprovação por
  leitura de saldo desatualizada (ataca I-05).
- Transferência confirmada cuja resposta se perde → reenvio do cliente duplica a
  movimentação sem idempotência (ataca I-04, I-07).

Mitigação de primeira ordem: as invariantes do §4 nascem **antes** da tecnologia; os
cenários críticos viram testes automatizados de aceitação (M2) e experimentos de
resiliência (M6).

### 8.2 Demais riscos relevantes

| Risco | Prob. | Impacto | Mitigação |
| ----- | ----- | ------- | --------- |
| Erosão das fronteiras modulares no monólito | Média | Extração futura cara | Verificação de dependências em build, revisão arquitetural na M6 |
| Escopo poliglota (4 stacks) exceder a capacidade de 12–15 h/semana | Média | Atraso de milestones | Núcleo primeiro; satélites são incrementais e descartáveis sem afetar o MVP |
| Dualidade outbox/consumidores mal implementada na M3 | Média | Eventos perdidos ou duplicados | Outbox na mesma transação do pagamento; consumidores com inbox/deduplicação; semântica at-least-once documentada |
| Metas de desempenho sem metodologia | Baixa | Números não defensáveis | Baseline com ambiente, carga e método publicados; revisão do alvo p95 após primeiros benchmarks |
| Complexidade operacional (K8s, IaC) antecipada demais | Baixa | Desvio de foco | Docker Compose até a M4; K8s local apenas na M5 |

### 8.3 Trade-offs assumidos conscientemente

- **Simplicidade transacional > demonstração precoce de distribuição.** Aceitamos um único
  runtime no início para ter invariantes verificáveis desde a primeira semana.
- **At-least-once + idempotência > exactly-once.** Na M3 aceitamos duplicatas toleradas
  pelos consumidores em vez de perseguir entrega exatamente-uma-vez.
- **Consistência forte no ledger, eventual nas projeções.** Consultas podem servir dados
  ligeiramente defasados, desde que o estado seja explícito e o registro contábil, exato.
- **Centavos inteiros > decimais arbitrários.** Elimina classes inteiras de erro de
  arredondamento ao custo de não suportar frações de centavo (fora de escopo).
- **Monorepo > multirepo.** Revisão e contratos centralizados ao custo de pipelines mais
  elaborados por serviço.

### 8.4 Critérios de sucesso verificáveis

O MVP (M1+M2) é bem-sucedido quando **todos** os critérios abaixo passam de forma
automatizada e documentada:

| # | Critério | Verificação |
| - | -------- | ----------- |
| C-01 | Integridade financeira | 100% dos cenários de teste preservam I-01..I-03; nenhum lançamento desbalanceado é aceito |
| C-02 | Idempotência | 100 requisições simultâneas com a mesma chave e payload → exatamente 1 transferência efetivada |
| C-03 | Concorrência | Transferências simultâneas jamais tornam negativo um saldo cuja política proíbe descoberto |
| C-04 | Rastreabilidade | Toda operação tem identificador único, histórico de estados e registros suficientes para investigação |
| C-05 | Recuperação | Operações confirmadas permanecem consultáveis após reinícios e falhas simuladas de comunicação |
| C-06 | Desempenho | Baseline publicado; meta experimental p95 < 300 ms sob carga definida e documentada, com metodologia |
| C-07 | Reprodutibilidade | Clone limpo sobe o ambiente com um comando; testes integrados ao CI |
| C-08 | Integrabilidade | Uma aplicação cliente integra-se usando apenas a documentação disponibilizada |

Para o projeto completo (24 semanas), somam-se os oito experimentos obrigatórios de
resiliência (transferências concorrentes, idempotência sob carga, queda do Kafka após
confirmação, replay de eventos, falha do antifraude, conciliação com duplicatas, reinício
durante processamento e rollback de versão com falha), cada um automatizado, executado e
documentado com resultados verificáveis.

---

## Referências

- Plano de engenharia PayCore (24 semanas, M1–M6) — documento de origem do projeto.
- Architecture Challenge #01 — diagnóstico inicial (problema, perguntas ao CTO, risco e critérios de MVP).
- Próximos documentos: ADR-001 (monólito modular), glossário expandido e contratos OpenAPI
  (cards da missão M1). A [ADR-002](../adr/ADR-002-persistencia-postgresql.md) e o
  [modelo de ameaças](../security/modelo-ameacas.md) já estão publicados.
