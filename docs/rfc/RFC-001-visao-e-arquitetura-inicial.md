# RFC-001 — PayCore: Visão e Arquitetura Inicial

| Campo | Valor |
| --- | --- |
| Status | Em revisão — proposta consolidada v0.9 |
| Autor | Luis Felipe (com apoio de execução via Gartrol/Claude Code) |
| Data | 2026-11-05 |
| Decisões vinculadas | ADR-001 (monólito modular, a escrever), ADR-002 (persistência e concorrência, a escrever) |
| Missão | M1 · Fundação arquitetural (semanas 1–4) |
| Fonte de consolidação | [Dossiê técnico-executivo RFC-001 v0.9](PayCore_RFC-001_Dossie_Tecnico_Executivo.pdf) e Architecture Reviews #01–#09 |

> **Nota de evidência:** este documento consolida decisões conceituais discutidas nas Architecture Reviews #01–#09. Ele não é evidência de produção, não substitui testes de implementação e ainda depende de revisão externa formal. A validação definitiva virá dos contratos persistentes, dos testes de invariantes e da publicação rastreada no repositório.

---

## 1. Contexto do problema e objetivos

O PayCore é uma plataforma financeira B2B simulada que permite a empresas criar contas, realizar transferências entre elas e acompanhar o resultado dessas operações de forma segura, confiável e rastreável — sem precisar construir essa infraestrutura internamente.

O valor central do produto **não é mover saldo**: é permitir que o cliente **confie no resultado** de cada operação. A pergunta crítica é: “a transferência foi efetivada?”. Se um cliente corporativo solicita uma transferência e ocorre uma falha de comunicação, ele precisa conseguir descobrir se a operação foi efetivada, rejeitada ou continua em processamento. Sem essa garantia, o cliente tende a repetir a requisição, gerando movimentações duplicadas e inconsistências financeiras.

O problema central, portanto, decompõe-se em três necessidades:

1. **Integridade das movimentações** — débitos e créditos corretos, atômicos e conservativos.
2. **Rastreabilidade das operações** — todo movimento tem identidade, histórico de estados e trilha de auditoria.
3. **Simplicidade de integração** — um cliente consegue integrar-se usando apenas a documentação pública.

A plataforma distingue três planos:

- **Fato contábil confirmado** — journal entry e postings balanceados no ledger.
- **Execução operacional** — a transferência como processo, com estados e recuperação.
- **Informação observável pelo cliente** — status, consultas e notificações.

Uma conexão interrompida jamais é, por si só, evidência de falha financeira.

### Objetivos do projeto

- Demonstrar capacidade de **projetar, implementar, testar, operar e evoluir** um sistema distribuído, justificando decisões de arquitetura e medindo comportamento sob falhas.
- Validar integridade, rastreabilidade e integração em um **ambiente fechado com saldos fictícios** (MVP de laboratório), sem dinheiro real.
- Construir uma base que possa evoluir (extração de serviços, novas tecnologias) **sem comprometer as invariantes do domínio financeiro**.

### Premissa arquitetural

> A confiabilidade das operações financeiras deve ser estabelecida **antes** de qualquer investimento em mecanismos avançados de escalabilidade. Uma plataforma que processa milhares de operações incorretamente não é melhor do que uma que processa centenas corretamente.

### Parâmetros do projeto

- Duração: 24 semanas (≈ 288–360 h, 12–15 h semanais), executadas por uma pessoa.
- Equipe simulada de referência: 3 engenheiros, 4 meses para o MVP. Essa régua vem do briefing/Challenge #01 e serve apenas para dimensionar escopo e risco; a execução real é solo, com os mesmos controles.
- Tecnologias-alvo: Java, Python, PHP e TypeScript — cada uma **onde faz sentido**, não como vitrine de frameworks.

---

## 2. Escopo e funcionalidades excluídas

### Dentro do escopo (MVP — Milestones 1 e 2)

- Instituição financeira **simulada**, com contas fictícias e ambiente fechado.
- Autenticação e autorização de clientes corporativos, com isolamento entre organizações.
- Criação e consulta de contas, suas condições de movimentação e posição financeira.
- Transferências internas, com estados explícitos, consulta por ID e por chave de idempotência.
- Ledger contábil de partidas dobradas, com journal entries e postings balanceados.
- Idempotência por `Idempotency-Key` com fingerprint de payload.
- Consulta de transações, estados e histórico.
- Obrigação durável de notificar, com tentativa, reenvio e consulta independente do resultado.

### Dentro do escopo (fases seguintes, fora do MVP)

- Publicação de eventos de domínio via Transactional Outbox + Kafka (M3).
- API de integração para parceiros e webhooks assinados com retry (M3).
- Motor de risco com regras determinísticas e decisões `APPROVE`/`REVIEW`/`REJECT` (M4).
- Conciliação com extratos externos simulados e relatório de divergências (M4).
- Observabilidade, IaC, CI/CD com segurança, SLOs e runbooks (M5).
- Chaos engineering, análise de escalabilidade/custos e release de portfólio (M6).

### Explicitamente excluído

- **Movimentação de dinheiro real**, integração com instituições financeiras reais, arranjos de pagamento (Pix, cartões, TED) e obrigações regulatórias (BACEN, PCI-DSS).
- Transações entre persistências financeiras independentes e compensações distribuídas.
- Reservas, descoberto, câmbio, tarifas e estorno parcial como recursos padrão.
- Garantia de entrega de notificações exatamente uma vez.
- Escala global ou separação de serviços sem evidência operacional.
- Interfaces de usuário finais; o produto é API-first.
- Garantias de produção real: metas de desempenho e disponibilidade são de laboratório.

---

## 3. Requisitos

### 3.1 Requisitos funcionais

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RF-01 | Criar contas financeiras simuladas | P0 |
| RF-02 | Consultar saldo disponível e histórico | P0 |
| RF-03 | Transferir valores entre contas | P0 |
| RF-04 | Garantir lançamentos contábeis balanceados | P0 |
| RF-05 | Evitar processamento duplicado por idempotência | P0 |
| RF-06 | Publicar eventos de pagamento com segurança | P1 |
| RF-07 | Processar eventos com consumidores idempotentes | P1 |
| RF-08 | Consultar transações e seus estados | P0 |
| RF-09 | Avaliar risco e aplicar bloqueios ou revisão | P1 |
| RF-10 | Conciliar transações com registros externos simulados | P1 |
| RF-11 | Emitir webhooks com assinatura e retry | P1 |
| RF-12 | Disponibilizar métricas operacionais e trilhas de auditoria | P1 |
| RF-13 | Cancelar transferência antes de qualquer confirmação, de forma idempotente | P0 |

P0 = obrigatório no MVP (M1+M2). P1 = necessário na plataforma, com implementação nas fases seguintes (M3–M5).

### 3.2 Atributos de qualidade

Metas de laboratório, não promessas de produção.

| Qualidade | Expectativa inicial |
| --- | --- |
| Correção | Conservação, atomicidade, unicidade e autorização sob concorrência |
| Durabilidade | Operação aceita e confirmada não desaparece silenciosamente |
| Rastreabilidade | Identificadores e cadeia de fatos e tentativas |
| Disponibilidade | Falha de consulta não reclassifica resultado financeiro |
| Desempenho | Metas experimentais aferidas em carga reproduzível |
| Operabilidade | Pendências e divergências acompanhadas e escalonadas |

- **Consistência** — nenhuma transferência concluída sem débito e crédito contábil correspondentes. Distinguimos consistência dos registros financeiros (forte, sempre) da consistência das projeções exibidas ao usuário (pode ser eventual, com estado explícito).
- **Idempotência** — repetição da mesma requisição com a mesma chave e payload não duplica a operação; mesma chave com payload diferente é rejeitada como conflito.
- **Integridade** — nenhum lançamento isolado quebra a igualdade entre débitos e créditos por moeda e por transação.
- **Disponibilidade/recuperação** — o sistema se recupera de interrupções simuladas sem perder operações já confirmadas; operações com resultado desconhecido são identificáveis.
- **Desempenho** — estabelecer baseline e buscar p95 < 300 ms para transferências internas no ambiente de teste. O número só é válido acompanhado de configuração do ambiente, volume de dados, concorrência, taxa de erros e metodologia.
- **Segurança** — autenticação, autorização por conta, validação de entradas e segredos fora do repositório.
- **Observabilidade** — logs estruturados, métricas, traces e identificadores de correlação (`trace_id`, `correlation_id`) propagados entre componentes.

---

## 4. Linguagem ubíqua e modelo de domínio

### Glossário

| Termo | Definição |
| --- | --- |
| **Account** | Identidade, proprietário, moeda, políticas e elegibilidade para débito/crédito. |
| **Financial Position** | Posição materializada usada para disponibilidade, consistente com os fatos contábeis. |
| **Transfer** | Intenção financeira imutável e máquina de estados. |
| **Journal Entry** | Unidade lógica contábil com conjunto completo de postings. |
| **Posting** | Débito ou crédito que integra um journal entry. |
| **Idempotency Key** | Identidade da solicitação no escopo do cliente e no prazo contratado. |
| **Notification Obligation** | Compromisso durável de comunicar mudança relevante. |
| **Authorization Context** | Referência às permissões e ao solicitante no aceite. |
| **Saldo** | Projeção derivada dos lançamentos efetivados; nunca é fonte primária da verdade. |
| **Liquidação** | Momento em que a transferência produz efeitos contábeis definitivos no ledger. |

### Agregados candidatos

```
Cliente (1) ──── (N) Conta
Conta   (1) ──── (N) Posting
Transfer (1) ──── (1) JournalEntry (1) ──── (2..N) Posting
Transfer (1) ──── (1) IdempotencyRecord
Transfer (1) ──── (N) NotificationObligation
```

- `accounts` — identidade, proprietário, moeda, política de descoberto, estado.
- `transfers` — origem, destino, valor, moeda, estado, `correlation_id`, timestamps por transição.
- `journal_entries` — agrupador atômico; só existe efetivado se balanceado.
- `postings` — débito/crédito em centavos; imutáveis após efetivação.
- `idempotency_records` — chave, fingerprint do payload, `transfer_id` e metadados de aceite; o replay retorna o recurso atual, não um snapshot congelado.
- `notification_obligations` — evento a comunicar, destino, estado, tentativas, próximo prazo e último erro.

**Account** conhece elegibilidade e disponibilidade; **Transfer** controla intenção e estados; **Ledger** protege balanceamento e imutabilidade. A confirmação financeira coordena invariantes que atravessam esses agregados. “Módulo”, “bounded context”, “agregado”, “transação” e “implantação” não são sinônimos.
---

## 5. Invariantes financeiras (INV-001 a INV-012)

Invariantes são propriedades que **nunca** podem ser violadas, em nenhum estado do sistema, inclusive durante falhas parciais, concorrência e reprocessamento.

| ID | Regra | Contrato verificável |
| --- | --- | --- |
| INV-001 | **Conservação** | Toda transferência interna concluída possui débitos e créditos balanceados por moeda. |
| INV-002 | **Atomicidade** | Não existe débito confirmado sem o crédito correspondente para a mesma transferência interna. |
| INV-003 | **Saldo disponível** | Sem descoberto, não se confirma débito superior à disponibilidade. |
| INV-004 | **Idempotência** | A mesma operação lógica não produz uma segunda movimentação por retry. |
| INV-005 | **Imutabilidade** | Postings confirmados não são editados ou apagados; correções geram novos fatos. |
| INV-006 | **Estado–ledger** | `COMPLETED` e confirmação financeira correspondem na fonte autoritativa e na mesma fronteira atômica. |
| INV-007 | **Fidelidade à intenção** | Contas, valor, moeda e autorização confirmados correspondem à intenção aceita. |
| INV-008 | **Exclusividade terminal** | Cancelamento, conclusão, rejeição e falha terminal são mutuamente exclusivos. |
| INV-009 | **Irreversibilidade** | Estado terminal não volta a processamento nem confirma nova efetivação original. |
| INV-010 | **Encerramento por evidência** | Não efetivação definitiva requer prova de ausência de movimento e vedação de futuras confirmações. |
| INV-011 | **Proteção concorrente** | Disponibilidade autoritativa é verificada e consumida no instante da confirmação. |
| INV-012 | **Unicidade contábil** | No máximo um journal entry original confirmado por transferência. |

Notas de domínio:

- INV-003 e INV-011 são relacionadas; a segunda explicita a propriedade concorrente.
- INV-004 trata repetição lógica; INV-012 trata unicidade da efetivação.
- A representação monetária na API é **decimal textual**, sem ponto flutuante binário; internamente, o ledger registra centavos inteiros.

Cenários críticos protegidos pelas invariantes:

- Conta com R$ 1.000 recebe duas transferências simultâneas de R$ 800 → apenas uma é aprovada; a outra é rejeitada por saldo insuficiente.
- Transferência confirmada no banco, resposta perdida na rede, cliente reenvia → nenhuma segunda movimentação; o cliente recebe o resultado original.
- Reprocessamento de eventos já consumidos → nenhum efeito financeiro duplicado.
- Cancelamento e confirmação concorrentes → somente um desfecho terminal, com ou sem postings, nunca ambos.

---

## 6. Máquina de estados e ciclo da operação

| Estado | Natureza | Terminal? |
| --- | --- | --- |
| `CREATED` | Solicitação duravelmente aceita | Não |
| `PROCESSING` | Execução ou recuperação em curso | Não |
| `COMPLETED` | Transferência e lançamentos confirmados atomicamente | Sim |
| `REJECTED` | Recusa definitiva por regra de negócio; sem efetivação | Sim |
| `FAILED` | Encerramento operacional definitivo sem efetivação | Sim |
| `CANCELLED` | Cancelamento efetivado antes de qualquer confirmação | Sim |

Transições candidatas:

```
CREATED ──► PROCESSING / REJECTED / CANCELLED / FAILED
PROCESSING ──► COMPLETED / REJECTED / FAILED / CANCELLED
```

- Todas as saídas para estados sem efetivação exigem evidência e exclusão de confirmação futura (INV-010).
- Nenhum estado terminal retorna a não terminal (INV-008, INV-009).
- Estorno é **outra operação vinculada**, não alteração do estado `COMPLETED`.
- `CREATED` só existe após persistência confirmada. Uma resposta `202` significa **responsabilidade registrada**, não transferência concluída.
- O resultado desconhecido da comunicação não altera o fato financeiro.
- O cancelamento efetivo só pode ocorrer antes de qualquer confirmação; se a confirmação vencer a corrida, o desfecho é `COMPLETED` e a correção é estorno.

---

## 7. Alternativas arquiteturais consideradas

### A. Microsserviços desde o início (4 serviços poliglotas)

| Prós | Contras |
| ---- | ------- |
| Demonstra integração poliglota desde o dia 1; fronteiras físicas fortes | Consistência distribuída **no núcleo financeiro** (sagas/2PC) antes de o domínio estar estável |
| Escala independente por serviço | 3 pessoas × 4 meses: custo operacional (deploys, observabilidade, contratos) consome o orçamento do MVP |
| | Depuração de invariantes (INV-001..INV-012) através de fronteiras de rede é drasticamente mais difícil |

**Rejeitada para o MVP.** Introduz o modo de falha mais perigoso (inconsistência distribuída) exatamente na parte mais sensível do sistema, sem necessidade demonstrada.

### B. Monólito único sem fronteiras internas

| Prós | Contras |
| ---- | ------- |
| Máxima simplicidade inicial; uma base de código, um deploy | Acoplamento cresce sem resistência; extração futura exige refatoração arriscada |
| Transações ACID locais triviais | Não exercita a competência-alvo (desenhar fronteiras de domínio) |

**Rejeitada.** Resolve o curto prazo, mas destrói a tese do projeto: evoluir para distribuído **quando houver razão**, com fronteiras já ensaiadas.

### C. Monólito modular em Java + extração dirigida por necessidade (escolhida)

| Prós | Contras |
| ---- | ------- |
| Transações ACID locais protegem INV-001..INV-006 com o mecanismo mais simples possível | Exige disciplina para manter módulos desacoplados (mitigável com ArchUnit/revisão) |
| Fronteiras lógicas (`accounts`, `transfers`, `ledger`, `shared`) ensaiam a extração futura | Um único runtime no início (aceitável no volume de laboratório) |
| Evolução incremental: Outbox/Kafka (M3) e serviços satélites (M4) entram **fora** do núcleo contábil | |

### D. Orientada a eventos desde o início (event sourcing no ledger)

| Prós | Contras |
| ---- | ------- |
| Trilha de auditoria nativa; replay natural | Curva de aprendizado e complexidade de projeções/versionamento de eventos no caminho crítico do MVP |
| | Partidas dobradas relacionais já fornecem auditabilidade exigida com ferramentas maduras |

**Rejeitada para o núcleo.** A mensageria entra na M3 como **integração** (Outbox + Kafka), não como fonte da verdade contábil.

### E. Persistências independentes com compensação

| Prós | Contras |
| ---- | ------- |
| Autonomia de dados e deploy | Compensação não equivale à atomicidade; exige sagas e reconciliação no núcleo |

**Adiada.** A confirmação indivisível dos fatos locais é mais simples e verificável com transação compartilhada.

### F. Processamento síncrono puro

| Prós | Contras |
| ---- | ------- |
| Resultado imediato quando disponível | Maior sensibilidade a timeout e a carga; pode esgotar o tempo da requisição |

**Candidata condicionada.** Útil quando a conclusão é barata e rápida.

### G. Processamento assíncrono puro

| Prós | Contras |
| ---- | ------- |
| Aceitação rápida e recuperação desacoplada | Exige monitoramento de pendências e contrato de consulta |

**Candidata condicionada.** Útil para picos e integrações externas futuras.

### H. Processamento híbrido (candidata)

Resposta terminal rápida (`201` com `COMPLETED` ou `REJECTED`) quando a confirmação for barata; `202` com `Location` e consulta quando houver dependência de processamento ou risco. Mais caminhos contratuais, mas preserva a recuperação por chave.

**Decisão:** a arquitetura escolhida é **C**, com processamento **H** como semântica observável. A confirmação conjunta favorece o MVP interno; integração externa futura muda a semântica da conclusão e exige novos estados e coordenação. A extração de serviços não é garantida apenas por organizar o código em módulos.
---

## 8. Arquitetura escolhida e justificativa

**Monólito modular em Java/Spring Boot (`financial-core`), PostgreSQL como fonte única da verdade contábil, evolução planejada para plataforma orientada a eventos com serviços satélites poliglotas.**

### Visão de referência (alvo ao fim das 24 semanas)

| Tecnologia | Componente | Justificativa |
| --- | --- | --- |
| Java + Spring Boot | Payment Core e Ledger | Transações, consistência, tipagem forte, concorrência e ecossistema corporativo |
| Python + FastAPI | Risk Engine | Análise de dados, regras de risco e evolução futura para modelos estatísticos |
| PHP + Laravel | Reconciliation | Rotinas de conciliação, importação de arquivos, agendamento e relatórios |
| TypeScript + NestJS | Integration API | Webhooks, contratos externos, integração com parceiros e APIs de borda |
| PostgreSQL | Persistência transacional | ACID, constraints, índices e controle de concorrência |
| Kafka | Eventos de domínio | Consumidores independentes, replay e processamento assíncrono |
| Redis | Cache e rate limiting | Dados temporários; **nunca** fonte da verdade financeira |

### Decisões estruturais

1. **Núcleo contábil local e ACID.** Contas, transferências e ledger vivem no mesmo processo e no mesmo banco. Débito, crédito, journal entry, estado, obrigação de notificação e registro de idempotência são efetivados em **uma transação**. É o mecanismo mais barato que satisfaz INV-001..INV-006. (ADR-002 detalhará bloqueio transacional vs. atualização condicional e ordem de aquisição de locks.)
2. **Módulos com fronteiras explícitas.** `accounts`, `transfers`, `ledger`, `shared` — dependências permitidas verificadas em build. A fronteira lógica de hoje é o contrato de extração de amanhã. (ADR-001.)
3. **Monorepo** (`services/`, `contracts/`, `infrastructure/`, `tests/`, `docs/`) para facilitar revisão arquitetural, versionamento de contratos e execução do laboratório completo.
4. **Contratos antes de implementação.** APIs especificadas em OpenAPI na semana 2; eventos com schema versionado na M3.
5. **Extração somente com gatilho explícito**: requisito de escala medido, necessidade de isolamento de linguagem/time, ou acoplamento operacional comprovado — nunca “porque microsserviços”. O ledger é o **último** candidato a extração; separar a contabilidade do processamento de pagamento introduziria consistência distribuída no ponto de maior risco.
6. **Serviços satélites entram pelas bordas.** Risk Engine, Reconciliation e Integration API consomem eventos/APIs do núcleo e falham de forma contida (timeout, circuit breaker, política explícita para indisponibilidade — ex.: pagamento sujeito a avaliação obrigatória **não** é aprovado silenciosamente quando o Risk Engine falha).
7. **Ledger como autoridade de fatos; posição materializada como autoridade operacional enquanto consistente.** A posição pode decidir débito apenas enquanto equivalente aos fatos contábeis; divergência exige contenção, não posting artificial.
8. **Obrigação de notificação na mesma fronteira de confirmação.** A entrega é separada; indisponibilidade de Notifications não altera `COMPLETED`.

---

## 9. Contratos HTTP observáveis

Base proposta: `/v1`. Todas as operações respeitam autenticação, autorização organizacional e consistência de representação. O cliente não infere sucesso financeiro de um `2xx` isolado: observa `status`.

| Operação / condição | Resposta proposta | Interpretação |
| --- | --- | --- |
| `POST /v1/transfers` · terminal imediato | `201` + `Location` + Transfer | Recurso criado; `status` indica `COMPLETED` ou `REJECTED` |
| `POST /v1/transfers` · em andamento | `202` + `Location` + Transfer | Aceito duravelmente, ainda sem desfecho |
| `POST` repetido · mesma intenção | `200` + Transfer atual | Mesma identidade, sem nova movimentação |
| `POST` repetido · intenção diferente | `409` + `IDEMPOTENCY_CONFLICT` | Chave não pode representar outra intenção |
| `POST /v1/transfers/{id}/cancellation` | `200` + Transfer atual | Cancelamento idempotente antes da confirmação; se a confirmação já venceu, retorna `COMPLETED` |
| `GET /v1/transfers/{id}` | `200` + Transfer | Consulta de recurso visível ao cliente |
| `GET /v1/transfers/by-idempotency-key/{key}` | `200` + Transfer | Recuperação após timeout |
| Consulta comprovadamente inexistente | `404` | Somente em fonte apta a provar ausência |
| Consulta indisponível | `503` | Não implica inexistência nem falha financeira |

**Semântica de replay:** a repetição com a mesma `Idempotency-Key` retorna o **recurso atual** (`200` + Transfer), preservando a identidade da operação; o registro de idempotência não congela um snapshot. Assim, uma criação que inicialmente respondeu `202` pode, em replay posterior, retornar `COMPLETED`, `REJECTED`, `FAILED` ou `CANCELLED`.

Exemplo:

```http
POST /v1/transfers
Authorization: Bearer <token>
Idempotency-Key: key-123
Content-Type: application/json

{"sourceAccountId":"ACC-001","destinationAccountId":"ACC-002",
 "amount":"500.00","currency":"BRL"}
```

```http
HTTP/1.1 202 Accepted
Location: /v1/transfers/TRF-001

{"id":"TRF-001","status":"PROCESSING","amount":"500.00",
 "currency":"BRL","sourceAccountId":"ACC-001",
 "destinationAccountId":"ACC-002","reason":null}
```

Questões ainda abertas: semântica exata de repetição concorrente durante `CREATED`, prazo de retenção das chaves, códigos para falhas pré-aceitação e percepção de clientes sobre `201` para transferências rejeitadas. O endpoint de recuperação por chave deve obedecer ao escopo do solicitante.

---

## 10. Concorrência, recuperação, reconciliação e notificações

### Concorrência entre contas e tentativas

O controle da disponibilidade deve ocorrer no limite de confirmação com isolamento suficiente. Duas transferências de R$ 800 contra R$ 1.000, sem outros movimentos e sem descoberto, levam a exatamente uma conclusão e uma rejeição, com saldo final de R$ 200. Atomicidade isolada não garante proteção contra decisões concorrentes obsoletas. A proteção decisiva ocorre na **disponibilidade autoritativa**, não na validação preliminar.

### Recuperação após resultado desconhecido

- Após timeout ou perda de conexão, preservar a chave e consultar a fonte autoritativa antes de qualquer repetição.
- Se `COMPLETED` e journal íntegro, retornar o fato existente, sem reexecutar.
- Se ausência de efetivação for comprovada e nenhuma execução antiga puder confirmar, permitir retomada sob a mesma identidade.
- Se o resultado não puder ser determinado, não informar `REJECTED` nem `FAILED`; manter acompanhamento e incidente.
- Execução antiga e recuperação concorrente não podem produzir dois journals originais confirmados (INV-012).

### Reconciliação

Ledger confirmado é a referência para fatos contábeis. Posição materializada pode ser usada para decidir débito apenas enquanto consistente com os fatos. Divergência entre saldos equivalentes exige contenção dos débitos afetados, investigação com instante/moeda/saldo inicial equivalentes, reconstrução controlada e auditoria. **Não se deve criar posting artificial para corrigir uma projeção defeituosa.**

### Notificações

A mesma fronteira de confirmação registra duravelmente a obrigação de comunicar, mas a entrega ocorre separadamente. Indisponibilidade de Notifications não altera `COMPLETED`. A entrega admite repetições com identificador estável; “exatamente uma vez” ao destinatário não é prometido. Política de retries, atraso máximo, destino inválido e retenção permanecem pendentes de validação de implementação.

---

## 11. Segurança, autorização e auditoria

- Identidade corporativa autenticada, autorização por organização e conta, isolamento de leitura e escrita entre clientes.
- Contexto de autorização da intenção preservado desde `CREATED`; revalidação de elegibilidade financeira no momento da confirmação.
- Dados essenciais da intenção são imutáveis; qualquer divergência de destino, valor ou moeda impede confirmação.
- Contas podem estar ativas ou bloqueadas; para o MVP, o bloqueio efetivo de débito impede nova confirmação, mesmo após aceite anterior.
- Trilha durável de identidade da operação, estados, motivos, journals, ações operacionais e obrigações de notificação.
- Evitar registrar tokens, senhas ou dados sensíveis desnecessários nos logs e checkpoints.
---

## 12. Riscos, trade-offs e decisões abertas

### 12.1 Maior risco

**Comprometer a integridade financeira durante o processamento de transferências, sob concorrência, falhas parciais ou repetição de requisições.** Materializações típicas:

- Duas transferências simultâneas de R$ 800 numa conta com R$ 1.000 → dupla aprovação por leitura de saldo desatualizada (ataca INV-003, INV-011).
- Transferência confirmada cuja resposta se perde → reenvio do cliente duplica a movimentação sem idempotência (ataca INV-004).

Mitigação de primeira ordem: as invariantes do §5 nascem **antes** da tecnologia; os cenários críticos viram testes automatizados de aceitação (M2) e experimentos de resiliência (M6).

### 12.2 Demais riscos e lacunas

| ID | Risco / lacuna | Prob. | Impacto | Tratamento recomendado |
| --- | --- | --- | --- | --- |
| R-01 | Isolamento insuficiente na posição financeira | Média | Crítico | Testes de interleavings e propriedade de não descoberto |
| R-02 | Erosão das fronteiras modulares no monólito | Média | Alto | Contratos internos, verificação de dependências em build, revisão arquitetural na M6 |
| R-03 | Escopo poliglota (4 stacks) exceder a capacidade semanal | Média | Alto | Núcleo primeiro; satélites são incrementais e descartáveis sem afetar o MVP |
| R-04 | Dualidade outbox/consumidores mal implementada na M3 | Média | Alto | Outbox na mesma transação do pagamento; consumidores com inbox/deduplicação; semântica at-least-once documentada |
| R-05 | Ambiguidade 201/202 e rejeição de negócio | Média | Médio | Publicar contrato uniforme e exemplos negativos |
| R-06 | Chaves expiradas e requisições concorrentes | Média | Médio | Definir escopo, retenção e identidade canônica |
| R-07 | Diferença entre ledger e posição | Média | Crítico | Prevenção transacional + reconciliação e contenção |
| R-08 | Operação `PROCESSING` abandonada | Média | Alto | Monitoramento >15 min (hipótese) e incidente >48 h |
| R-09 | Notificações permanentemente malsucedidas | Média | Médio | Tentativas, observabilidade, reprocessamento e retenção |
| R-10 | Estornos e bloqueios sem política | Média | Médio | Política explícita antes de incluir no MVP |
| R-11 | Dependência futura de instituição externa | Baixa | Alto | Revisar semântica de `COMPLETED` e fronteiras |
| R-12 | Metas de desempenho sem metodologia | Baixa | Médio | Baseline com ambiente, carga e método publicados; revisão do alvo p95 após benchmarks |
| R-13 | Complexidade operacional (K8s, IaC) antecipada | Baixa | Médio | Docker Compose até a M4; K8s local apenas na M5 |

### 12.3 Trade-offs assumidos conscientemente

- **Simplicidade transacional > demonstração precoce de distribuição.** Aceitamos um único runtime no início para ter invariantes verificáveis desde a primeira semana.
- **At-least-once + idempotência > exactly-once.** Aceitamos duplicatas toleradas pelos consumidores em vez de perseguir entrega exatamente-uma-vez.
- **Consistência forte no ledger, eventual nas projeções.** Consultas podem servir dados ligeiramente defasados, desde que o estado seja explícito e o registro contábil, exato.
- **Centavos inteiros internos + decimal textual na API > decimais arbitrários.** Elimina classes de erro de arredondamento; a conversão é responsabilidade da borda.
- **Monorepo > multirepo.** Revisão e contratos centralizados ao custo de pipelines mais elaborados por serviço.
- **Transação compartilhada > persistências independentes.** Confirmação indivisível dos fatos locais ao custo de contenção e dependência da fronteira de persistência.
- **Processamento híbrido > síncrono puro.** Resposta terminal rápida quando possível, `202` com consulta quando necessário, ao custo de mais caminhos contratuais.

### 12.4 Decisões a fechar antes do desenvolvimento do núcleo

- Especificar modelo monetário, moeda, saldo inicial, reservas (excluídas inicialmente) e precisão.
- Definir isolamento, exclusividade e vínculos persistentes de transferência–journal–posição.
- Determinar contrato definitivo de idempotência e retenção.
- Validar semântica de HTTP 201/202/200/409/404/503 com clientes de exemplo.
- Determinar política de reconciliação, contenção, estorno e notificações.
- Mensurar e comparar desempenho real com metas experimentais.

---

## 13. Critérios de aceite verificáveis

O MVP (M1+M2) é bem-sucedido quando **todos** os critérios abaixo passam de forma automatizada e documentada. As metas são planejadas, não testes executados; critérios de 100% se referem à suíte executada e não provam ausência universal de defeitos.

| Código | Cenário | Resultado verificável proposto |
| --- | --- | --- |
| CA-001 | Transferência de R$ 500 com A = R$ 1.000 | A = R$ 500, B recebe R$ 500, 1 journal balanceado |
| CA-002 | Duas transferências distintas de R$ 800 contra R$ 1.000 | 1 `COMPLETED`, 1 `REJECTED`, saldo R$ 200 |
| CA-003 | 100 POSTs concorrentes com mesma chave/intenção | 1 transferência lógica, no máximo 1 efetivação; outra intenção = 409 |
| CA-004 | Resposta perdida após confirmação | Retry pela mesma chave recupera `COMPLETED` sem novos lançamentos |
| CA-005 | Falha antes/durante/depois da confirmação | Sem efeitos parciais nem duplicação; recuperação segura |
| CA-006 | Divergência controlada posição/ledger | Detecção, bloqueio de débito, reconstrução auditável |
| CA-007 | Cancelamento compete com confirmação | Somente `CANCELLED` sem postings OU `COMPLETED` com postings |
| CA-008 | Notifications indisponível durante conclusão | `COMPLETED` mantido, obrigação durável e reentrega |
| CA-009 | 20 TPS por 10 minutos; contas independentes | 12.000 operações nominais, p95 síncrono < 300 ms (meta experimental) |
| CA-010 | `PROCESSING` prolongado | Pendência detectada e recuperada/escalonada sem desfecho falso |
| CA-011 | Clone limpo do repositório | Ambiente sobe com um comando; testes integrados ao CI |
| CA-012 | Integração por documentação | Uma aplicação cliente integra-se usando apenas a documentação disponibilizada |

CA-009 exige medir separadamente latência HTTP, tempo até estado terminal, porcentagem de `202`, falhas e pendências. O baseline só é defensável acompanhado de configuração do ambiente, volume, concorrência, taxa de erros e metodologia.

Para o projeto completo (24 semanas), somam-se os oito experimentos obrigatórios de resiliência: transferências concorrentes, idempotência sob carga, queda do Kafka após confirmação, replay de eventos, falha do antifraude, conciliação com duplicatas, reinício durante processamento e rollback de versão com falha — cada um automatizado, executado e documentado com resultados verificáveis.

---

## 14. Próximos passos e governança

| Etapa | Entregável | Evidência |
| --- | --- | --- |
| 1. Consolidar RFC | Documento versionado com decisões e pendências | Revisão humana registrada |
| 2. Contratos persistentes | Restrições de unicidade, isolamento e modelo monetário | Especificação e testes de propriedades |
| 3. Contratos públicos | API documentada com exemplos negativos | Testes de contrato de cliente |
| 4. Núcleo financeiro | Fluxos de confirmação e recuperação | CA-001 a CA-007 automatizados |
| 5. Comunicação e operação | Obrigações, reentrega, alarmes, reconciliação | CA-006, CA-008 e CA-010 |
| 6. Qualidade de carga | Benchmarks reproduzíveis | CA-009 com métricas reais |

Gate de conclusão do card: todas as seções preenchidas, alternativas comparadas, critérios verificáveis, revisão por outra pessoa e publicação comprovada no repositório. Este documento consolida o conteúdo; a validação formal e a evidência de publicação permanecem rastreadas pelo fluxo de revisão e pelo Git.

---

## 15. Revisão e publicação

### Checklist da RFC-001

- [x] Definição do problema e contexto de negócio
- [x] Requisitos funcionais e não funcionais
- [x] Invariantes financeiras
- [x] Modelo de domínio e bounded contexts
- [x] Alternativas arquiteturais e trade-offs
- [x] Riscos técnicos e operacionais
- [x] Critérios de aceite verificáveis
- [ ] Revisão formal por outra pessoa registrada
- [x] RFC-001 publicada no GitHub (branch/topic de revisão; integração depende de aprovação humana)

### Aprovações

| Papel | Nome | Data | Decisão |
| --- | --- | --- | --- |
| Autor | Luis Felipe | 2026-11-05 | Proposto v0.9 |
| Revisor humano | Staff Engineer / Architecture Review | — | Pendente |
| Product Owner | — | — | Pendente |

### Registro de revisão

- **2026-11-05** — Consolidação da RFC-001 a partir do dossiê técnico-executivo v0.9 e das Architecture Reviews #01–#09. Conteúdo publicado em branch de revisão; aguardando aprovação humana formal para marcar como **Aceito**.
- **2026-11-06** — Fidelidade da RFC ao dossiê técnico-executivo v0.9 verificada por extração e comparação textual (seções 01–12, INV-001–012, CA-001–010); o PDF do dossiê foi anexado ao repositório como evidência de consolidação. Publicação da consolidação v0.9 em branch de revisão; aprovação humana formal permanece pendente.

---

## Referências

- Plano de engenharia PayCore (24 semanas, M1–M6) — documento de origem do projeto.
- Architecture Challenge #01 — diagnóstico inicial (problema, perguntas ao CTO, risco e critérios de MVP).
- Dossiê técnico-executivo RFC-001 v0.9 — consolidação das Architecture Reviews #01–#09.
- Próximos documentos: ADR-001 (monólito modular), ADR-002 (persistência e concorrência), glossário expandido e contratos OpenAPI (cards da missão M1).
