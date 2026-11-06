# ADR-002 — Persistência transacional em PostgreSQL

| Campo | Valor |
| ----- | ----- |
| Status | Aceito |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-06 |
| Depende de | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md), [Requisitos, invariantes e glossário](../requirements/requisitos-invariantes-glossario.md), [Modelo de domínio](../architecture/modelo-dominio-estados-transferencia.md) |
| Missão | M1 — Fundação arquitetural |
| Decisão vinculada | [Modelo de ameaças](../security/modelo-ameacas.md) (T-04, T-05, T-06, T-08, T-16, T-20) |

## Contexto

O PayCore precisa provar que uma transferência concluída tem débito e crédito
correspondentes, que uma repetição de requisição não duplica dinheiro e que uma operação
confirmada sobrevive a falhas. Essas propriedades são invariantes do domínio (I-01..I-08),
não detalhes de implementação.

O núcleo atual é um monólito modular Java/Spring Boot com PostgreSQL. Contas já existem;
transferências, journal entries e postings têm schema inicial, mas a efetivação atômica, a
idempotência completa e as constraints de integridade ainda serão implementadas no M2. Esta
ADR fixa a **estratégia de persistência e concorrência** antes dessa implementação, para
evitar migração e retrabalho de risco.

### Forças em jogo

- Consistência forte no ledger é obrigatória; projeções podem ser eventuais, mas o registro
  contábil não.
- O volume é de laboratório, mas o desenho não pode depender de serialização global.
- A equipe é pequena: cada mecanismo adicional precisa pagar seu custo operacional.
- O sistema deve evoluir para eventos (M3) sem transformar o ledger em event sourcing.
- O ambiente local precisa ser reproduzível com Docker Compose; o CI precisa rodar o mesmo
  schema com Testcontainers.

## Alternativas consideradas

### A. PostgreSQL com transações ACID e locks explícitos (escolhida)

| Prós | Contras |
| ---- | ------- |
| Transações locais resolvem I-01..I-06 com o mecanismo mais simples | Exige disciplina de locking e migrations |
| Constraints, índices, `SERIALIZABLE` e PITR disponíveis | Um único banco concentra disponibilidade |
| Ferramental maduro e conhecimento transferível | Escala vertical tem limite (aceitável no laboratório) |

### B. Banco de documentos (MongoDB ou similar)

| Prós | Contras |
| ---- | ------- |
| Modelo flexível para eventos e documentos | Sem transações multi-documento tão simples quanto ACID relacional para o ledger |
| Escala horizontal facilitada | Constraints de integridade e joins de auditoria ficam na aplicação |
| | Risco de consistência no ponto mais sensível do sistema |

### C. Event sourcing como fonte da verdade

| Prós | Contras |
| ---- | ------- |
| Trilha de auditoria e replay naturais | Projeções, versionamento e rebuild no caminho crítico do MVP |
| | Partidas dobradas relacionais já dão auditabilidade com ferramentas maduras |
| | Complexidade desproporcional à equipe e ao prazo |

### D. MySQL ou outro relacional

| Prós | Contras |
| ---- | ------- |
| ACID e maturidade equivalentes em grande parte | Ecossistema local/CI e recursos de locking/observabilidade menos alinhados ao stack |
| | Sem ganho relevante que justifique a troca |

**Decisão:** PostgreSQL. As alternativas B e C são rejeitadas para o núcleo contábil; a D
não apresenta vantagem suficiente para justificar o custo de troca.

## Decisão

Adotamos **PostgreSQL 16** como fonte única da verdade contábil, com as seguintes regras.

### 1. Um banco, um schema, migrations imutáveis

- Um banco por ambiente (`paycore` local, `paycore_it` em testes), schema gerenciado por
  **Flyway** em `services/financial-core/app/src/main/resources/db/migration`.
- Migrations são **imutáveis** depois de aplicadas; correções entram em nova versão.
- O CI sobe um banco limpo, aplica todas as migrations e roda os testes de integração.
- Mudanças destrutivas seguem **expand/contract**: adicionar, migrar dados, só então remover
  em uma migration posterior.

### 2. Contas e saldo

- `accounts` guarda identidade, proprietário, moeda, política de descoberto, estado e
  `created_at`.
- **Saldo não é coluna.** É a projeção `SUM(credits) - SUM(debits)` dos postings efetivados,
  filtrada por conta e moeda. Isso elimina a classe de bug em que saldo e ledger divergem.
- Consultas de saldo usam índice por conta e são cacheadas apenas se o cache for invalidado
  de forma explícita; cache nunca é fonte da verdade.

### 3. Ledger append-only e balanceado

- `journal_entries` é o agrupador atômico; `postings` são as partidas individuais.
- Um `JournalEntry` só é efetivado se, na mesma transação, a soma dos `CREDIT` for igual à
  soma dos `DEBIT` por moeda. A validação é feita no serviço antes do commit e reforçada por
  teste de invariante.
- `postings` não recebem `UPDATE` nem `DELETE` pela aplicação. Correções são um novo
  `JournalEntry` compensatório (I-03). A proteção no banco (trigger ou role sem `UPDATE`/
  `DELETE`) entra junto com o ledger no M2.
- Valores são `BIGINT` em centavos, sempre positivos; o sinal vem de `direction`. Isso evita
  arredondamento e torna a soma exata.
- `transfers.journal_entry_id` é único na prática (1:1); a constraint explícita acompanha a
  implementação do ledger.

### 4. Transferências e máquina de estados

- `transfers` guarda origem, destino, valor, moeda, `correlation_id`, estado,
  `failure_reason` e timestamps.
- A transição de estado e a efetivação contábil acontecem na **mesma transação** quando o
  estado final é `COMPLETED`.
- `REJECTED` e `FAILED` são terminais e não produzem postings parciais. `FAILED` é falha
  técnica após o aceite; `REJECTED` é decisão de negócio antes da efetivação (I-07).
- O histórico de transições é registrado com timestamp e motivo; a tabela dedicada de
  histórico entra no M2, substituindo a leitura implícita de `updated_at`.

### 5. Idempotência

- A chave é escopada por **proprietário + operação**: unicidade em
  `(owner_id, idempotency_key)`, não global.
- O fingerprint é `SHA-256` do payload relevante (origem, destino, valor, moeda e demais
  campos que mudam a intenção).
- O protocolo é “inserir primeiro, processar depois”:
  1. `INSERT` do registro de idempotência com fingerprint e estado `PROCESSING`; a unique
     constraint serializa concorrentes.
  2. Quem perde a corrida lê o registro existente: mesmo fingerprint → aguarda/retorna o
     snapshot; fingerprint diferente → `409 idempotency_key_conflict`; ainda `PROCESSING`
     além do limite → `409 idempotency_key_in_progress`.
  3. Em caso de falha antes do commit, o registro é removido na mesma transação, liberando
     a chave para retry legítimo.
  4. O snapshot da resposta é persistido junto do resultado e é imutável.
- A retenção da chave deve ser maior que a janela máxima de retry do cliente; o valor inicial
  de laboratório é **30 dias**, revisável na M3.
- A tabela `transfers.idempotency_key` atual é um placeholder global do bootstrap; o M2
  introduz `idempotency_records` com escopo por proprietário e migra os dados existentes.

### 6. Concorrência e isolamento

O nível padrão é **`READ COMMITTED`**. Para transferências, o protocolo é:

1. Abrir transação curta.
2. Carregar a transferência e validar estado/idempotência.
3. Bloquear as contas envolvidas com `SELECT ... FOR UPDATE` em **ordem determinística de
   `id`** (menor UUID primeiro), evitando deadlock entre transferências cruzadas.
4. Recalcular saldo disponível dentro da transação, após o lock.
5. Validar política de descoberto (I-05), moeda e estado das contas.
6. Inserir `JournalEntry` + `Posting[]` e validar o balanceamento.
7. Atualizar a transferência para `COMPLETED` e persistir o snapshot de idempotência.
8. Commit. Em falha, rollback completo; nada parcial é visível.

`SERIALIZABLE` não é o padrão porque aumenta abortos e exige retry em toda operação, sem
ganho para o padrão de acesso atual. Ele pode ser usado pontualmente em uma operação
analítica ou de fechamento, com retry explícito. Atualizações condicionais
(`UPDATE ... WHERE saldo >= valor`) são uma alternativa aceitável para operações simples,
mas não substituem o lock quando é preciso validar múltiplas invariantes antes do commit.

### 7. Constraints e índices mínimos

| Regra | Onde | Estado |
| ----- | ---- | ------ |
| `amount > 0` em transfers e postings | `CHECK` | Implementado (postings) / planejado (transfers) |
| `direction IN ('DEBIT','CREDIT')` | `CHECK` | Implementado |
| Moeda com 3 caracteres | `VARCHAR(3)` | Implementado |
| Conta de origem ≠ destino | `CHECK` | Implementado |
| `journal_entries.transfer_id` único | `UNIQUE` | Implementado |
| Idempotência única por proprietário | `UNIQUE (owner_id, key)` | Planejado M2 |
| Soma de postings = 0 por entry/moeda | Serviço + teste; trigger avaliado no M2 | Planejado M2 |
| Postings imutáveis | Role/trigger | Planejado M2 |
| Índices de consulta por conta e data | `CREATE INDEX` | Parcial (conta); data no M2 |

### 8. Acesso, segredos e operação

- A aplicação usa uma role de runtime com apenas os privilégios DML necessários. A role de
  migration é separada e usada apenas no deploy. Nenhuma das duas é superuser.
- Credenciais vêm de variáveis de ambiente; o valor padrão de `application.yml` é exclusivo
  do laboratório e deve ser substituído em qualquer ambiente compartilhado.
- TLS é obrigatório fora do laboratório; `sslmode=require` no JDBC.
- Pool de conexões limitado; timeouts de query e de lock definidos; métricas de conexões,
  locks e slow queries exportadas na M5.
- Backup: `pg_dump` documentado para o laboratório; PITR e retenção gerenciada na M5. Um
  teste de restauração faz parte da definição de pronto da M5/M6.
- Dados de laboratório podem ser recriados; nenhum dado real entra no banco.

### 9. Observabilidade e auditoria

- Toda operação carrega `correlation_id`; logs estruturados incluem o identificador, sem
  valores de payload sensíveis.
- Transições de estado têm timestamp e motivo; a trilha é consultável por operação.
- Métricas mínimas: latência de commit, conexões ativas, espera por lock, abortos e erros de
  serialização. Elas entram com o ledger (M2) e são expostas na M5.
- Eventos de domínio (M3) saem por **Transactional Outbox** na mesma transação; publicação
  não pode anteceder o commit.

## Consequências

### Positivas

- As invariantes financeiras têm um mecanismo único, testável e amplamente compreendido.
- O ledger permanece independente de mensageria; Kafka entra como integração, não como
  fonte da verdade.
- O caminho de evolução para serviços satélites não exige reescrever o núcleo contábil.
- O CI consegue reproduzir o schema e as transações em um banco real.

### Negativas

- O banco é um ponto único de falha no laboratório; disponibilidade real depende da M5.
- Locks explícitos exigem disciplina e testes de concorrência; erros de ordem podem gerar
  deadlocks.
- A validação de balanceamento no serviço precisa de teste rigoroso, pois o banco sozinho
  não expressa a soma entre linhas de forma simples.
- O custo de migrations imutáveis é maior em mudanças destrutivas.

### Riscos aceitos

| Risco | Dono | Mitigação | Revisão |
| ----- | ---- | --------- | ------- |
| Contenção/deadlock em transferências concorrentes | Backend | Ordem determinística, transação curta, retry, teste de concorrência | M2 |
| Indisponibilidade do PostgreSQL local | Plataforma | Ambiente recriável, backup, PITR/HA na M5 | M5 |
| Erro de balanceamento não detectado | Backend | Teste de invariante I-01, validação antes do commit, trigger avaliado | M2 |
| Role única no laboratório | Plataforma | Separar runtime/migration na M5 | M5 |

## Estado da implementação

| Item | Estado em 2026-11-06 |
| ---- | -------------------- |
| PostgreSQL + Docker Compose | Implementado |
| Flyway e migrations V1–V3 | Implementado |
| Contas e autorização por proprietário | Implementado |
| Schema inicial de transfers/journal/postings | Implementado (esqueleto) |
| Efetivação atômica e locks | Planejado M2 |
| Idempotência completa | Planejado M2 |
| Postings imutáveis no banco | Planejado M2 |
| Outbox transacional | Planejado M3 |
| PITR, HA e restore drill | Planejado M5/M6 |

## Critérios de revisão

Esta ADR deve ser revista se:

- o teste de concorrência do M2 mostrar abortos ou deadlocks inaceitáveis;
- o volume medido exigir particionamento, réplica de leitura ou outro banco;
- uma exigência regulatória ou de cliente impuser retenção/isolamento diferente;
- o Transactional Outbox do M3 revelar acoplamento que justifique separar o ledger;
- o custo operacional de PITR/HA for incompatível com o orçamento do projeto.

A revisão é feita via nova ADR; esta permanece no histórico como `Substituído`.

## Referências

- [RFC-001 — Visão e Arquitetura Inicial](../rfc/RFC-001-visao-e-arquitetura-inicial.md)
- [Requisitos, invariantes e glossário](../requirements/requisitos-invariantes-glossario.md)
- [Modelo de domínio e máquina de estados](../architecture/modelo-dominio-estados-transferencia.md)
- [Modelo de ameaças inicial](../security/modelo-ameacas.md)
- [Autenticação e autorização de contas](../architecture/autenticacao-e-autorizacao-contas.md)
- [`infrastructure/docker/docker-compose.yml`](../../infrastructure/docker/docker-compose.yml)
- [`services/financial-core/app/src/main/resources/db/migration`](../../services/financial-core/app/src/main/resources/db/migration)
