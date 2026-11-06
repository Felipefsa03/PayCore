# Estratégia de testes e matriz de evidências por camada

| Campo | Valor |
| ----- | ----- |
| Status | Vigente |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-06 |
| Depende de | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md), [Requisitos e invariantes](../requirements/requisitos-invariantes-glossario.md), [ADR-002](../adr/ADR-002-persistencia-postgresql.md), [Modelo de ameaças](../security/modelo-ameacas.md) |
| Missão | M1 — Fundação arquitetural |
| Evidência da M1 | [Relatório de execução 2026-11-06](evidencias/M1-2026-11-06.md) |

## Objetivo

Definir como o PayCore prova que cada camada funciona, qual comando produz a evidência, qual
cobertura é esperada e onde o relatório é publicado. A estratégia evita dois extremos: testes
unitários que não tocam o banco (e não provam transações) e testes de integração lentos usados
para tudo.

A regra central é: **cada requisito ou invariante P0 tem um teste automatizado na camada mais
barata que ainda prova o comportamento real**. Quando a prova depende de transação, lock ou
constraint, ela é de integração com PostgreSQL real.

## 1. Princípios

1. **Testar comportamento, não implementação.** O teste nomeia a regra de negócio ou a
   invariante (ex.: “outro proprietário não lê a conta”), não o método interno.
2. **Banco real para o que é transacional.** O ledger e a idempotência só são provados com
   PostgreSQL e Flyway; mocks de repositório não demonstram commit, rollback ou lock.
3. **Determinismo.** Cada teste cria seus próprios dados e não depende de ordem, relógio ou
   resíduo de execução anterior. Testcontainers sobe um banco limpo; o modo externo usa um
   banco dedicado de integração.
4. **Falha explícita.** Um teste que não pode rodar (Docker ausente, banco indisponível) falha
   com mensagem acionável; não é silenciosamente ignorado.
5. **Evidência publicada.** O resultado não fica no terminal: Surefire, JaCoCo e Checkstyle
   são artefatos do CI e o relatório da milestone linka o comando e o artefato.
6. **Pirâmide adaptada.** Muitos testes unitários rápidos, uma camada de integração com banco
   real e poucos testes transversais (contrato, e2e, performance) onde agregam sinal.

## 2. Camadas, comandos e propósito

| Camada | Ferramenta | Comando | O que prova | Status M1 |
| ------ | ---------- | ------- | ----------- | --------- |
| Unitário | JUnit 5 + AssertJ/Mockito | `make test-unit` | Regras puras, validação de entrada, aritmética monetária e autorização sem I/O | Implementado (12 testes) |
| Integração | Spring Boot Test + MockMvc + PostgreSQL + Flyway | `make test-integration` | Contrato HTTP, persistência, migrations, autorização por proprietário e consultas reais | Implementado (12 testes) |
| Gate completo | Maven `verify` + Checkstyle + JaCoCo | `make test` | Compilação, estilo, testes das duas camadas e relatório de cobertura | Implementado (24 testes) |
| Contrato | Redocly + openapi-typescript | `npx @redocly/cli lint contracts/openapi/financial-core.yaml` | Sintaxe, referências e consistência do contrato público | Parcial: validado manualmente; automação no CI planejada para M2 |
| Estático | Checkstyle | `make lint` | Convenções de código e ausência de violações bloqueantes | Implementado |
| Documentação | checker próprio | `make docs-check` | Links relativos e âncoras da documentação | Implementado |
| Segurança documental | checker próprio | `make security-check` | Dono, mitigação e risco residual em cada ameaça | Implementado |
| E2E | HTTP contra ambiente composto | `make up && make run` + cenário | Fluxo completo cliente → API → banco | Planejado M2 |
| Performance | k6/Gatling | cenário versionado | p95, throughput, erro sob carga declarada | Planejado M2 |
| Resiliência | caos controlado | cenário versionado | Durabilidade e recuperação após falha | Planejado M6 |

### Pré-requisitos por camada

- **Unitário:** Java 21. Não requer banco nem Docker.
- **Integração:** Docker + Compose (Testcontainers) **ou** `PAYCORE_IT_JDBC_URL`,
  `PAYCORE_IT_DB_USER` e `PAYCORE_IT_DB_PASSWORD` apontando para um PostgreSQL 16 dedicado.
  O banco é migrado pelo Flyway antes dos cenários.
- **Gate completo:** Java 21 e uma das opções de banco acima.
- **Contrato:** Node.js com `npx` para Redocly/openapi-typescript (não disponível em todos os
  ambientes locais; o CI pode executá-lo).
- **E2E/performance:** ambiente composto e ferramenta de carga; comandos serão fixados no
  card da M2.

## 3. Cobertura declarada

A cobertura é medida por **linhas e branches** com JaCoCo 0.8.12. O relatório é gerado no
`verify` de cada módulo e publicado como artefato. Cobertura não é uma meta isolada: uma linha
coberta por um teste sem asserção de negócio não conta como prova.

### Baseline medido na M1

| Módulo | Linhas | Branches | Comentário |
| ------ | ------ | -------- | ---------- |
| Shared | 14/28 (50,0%) | 4/12 (33,3%) | `TransferState` ainda sem teste; `Money` cobre o essencial |
| Accounts | 40/43 (93,0%) | 5/6 (83,3%) | Serviço e modelo bem cobertos por unidade |
| App | 132/158 (83,5%) | 12/16 (75,0%) | API, segurança e tratamento de erro cobertos por integração |
| **Total** | **186/229 (81,2%)** | **21/34 (61,8%)** | Gate atual do laboratório |

### Política

| Escopo | Meta | Estado em 2026-11-06 | Ação |
| ------ | ---- | --------------------- | ---- |
| Linhas, total | ≥ 80% | 81,2% — atende | Manter |
| Branches, total | ≥ 70% | 61,8% — não atende | Fechar lacunas no M2 |
| `accounts` (domínio) | ≥ 85% linhas | 93,0% — atende | Manter |
| `app` (API) | ≥ 80% linhas | 83,5% — atende | Manter |
| `shared` | ≥ 70% linhas | 50,0% — não atende | Testar `TransferState` no M2 |
| Código novo (diff) | ≥ 80% linhas | Não medido | Adotar ferramenta de diff coverage no M2 |

**Enforcement:** o relatório JaCoCo é gerado e publicado desde já; o `jacoco:check` bloqueante
entra no M2, depois de cobrir `TransferState` e o caminho de erro do ledger. Até lá, a revisão
humana usa o relatório do CI. Declarar a meta sem fingir que ela já bloqueia é parte da
estratégia: a lacuna está visível e tem dono.

### Como reproduzir a cobertura

```sh
make test                 # gera target/site/jacoco em cada módulo
make coverage-summary     # resume linhas/branches por módulo
```

O CI publica `services/financial-core/**/target/site/jacoco/**` no artefato
`financial-core-reports`, junto de Surefire e Checkstyle.

## 4. Matriz de evidências

A matriz completa, requisito por requisito, está em
[`matriz-evidencias.md`](matriz-evidencias.md). Ela distingue explicitamente **implementado**,
**parcial** e **planejado** — não trata requisito futuro como se já tivesse teste.

Resumo da M1:

| Camada | Evidência | Onde verificar |
| ------ | --------- | -------------- |
| Unitário | 12 testes, 0 falhas | Surefire de `shared`, `accounts` e `app`; `MoneyTest`, `AccountServiceTest`, `OwnerIdValidatorTest` |
| Integração | 12 testes, 0 falhas | `AccountApiIntegrationTest` e `FinancialCoreApplicationTest` |
| Gate completo | 24 testes, 0 falhas, 0 violações Checkstyle | `make test` no clone limpo |
| Cobertura | 81,2% linhas / 61,8% branches | JaCoCo HTML/CSV + `make coverage-summary` |
| Contrato | Lint e tipos gerados registrados | [`modelo-dominio-estados-transferencia.md`](../architecture/modelo-dominio-estados-transferencia.md#4-validação-do-contrato-passo-de-confirmação-do-card) e `contracts/openapi/generated/financial-core.d.ts` |
| Documentação | Links e âncoras válidos | `make docs-check` |
| Segurança | 24 ameaças completas | `make security-check` |

## 5. CI e publicação de artefatos

O workflow [`financial-core-ci.yml`](../../.github/workflows/financial-core-ci.yml) executa
`./mvnw -B verify` em Java 21 para push em `main` e pull requests que toquem o serviço. O
`verify` roda compilação, Checkstyle, testes unitários e de integração (Testcontainers) e gera
JaCoCo.

Depois do gate, o passo **Upload test, coverage and lint reports** publica:

- `surefire-reports/**` — XML/TXT por teste, com tempo e falha;
- `site/jacoco/**` — HTML navegável e CSV por classe;
- `checkstyle-result.xml` — violações e arquivos analisados.

O artefato fica retido pelo prazo padrão do GitHub Actions e é a evidência oficial do PR. O
relatório da milestone copia apenas o resumo e o link do artefato, nunca logs brutos.

O workflow [`docs-ci.yml`](../../.github/workflows/docs-ci.yml) valida links, âncoras e o
modelo de ameaças em mudanças de documentação.

## 6. Dados, isolamento e ambientes

- **Testcontainers** sobe um PostgreSQL descartável por execução, aplica Flyway e derruba o
  container ao final. É o caminho padrão do CI.
- **Banco externo** (`PAYCORE_IT_*`) é uma alternativa para ambientes sem Docker. O banco
  deve ser dedicado a testes; os cenários criam dados com UUIDs próprios e não assumem estado
  anterior. Nunca use o banco de demonstração ou dados reais.
- **Relógio e ordem:** testes não dependem de horário do sistema; quando o tempo importa, o
  valor é injetado ou comparado com tolerância explícita.
- **Segredos:** tokens de teste são gerados no próprio teste com segredo de laboratório. Nunca
  são copiados de ambiente real nem impressos em log.
- **Paralelismo:** o build atual roda os módulos em sequência. Se o paralelismo for habilitado,
  cada classe de integração precisa de dados isolados e banco próprio.

## 7. Lacunas e roadmap

| Lacuna | Risco | Dono | Quando |
| ------ | ----- | ---- | ------ |
| `TransferState` sem teste (0/7 linhas) | Regressão silenciosa na máquina de estados | Backend | M2, antes do fluxo de transferência |
| Sem teste de concorrência/idempotência | Duplicidade ou deadlock não detectado | Backend | M2 |
| Sem teste de ledger balanceado | I-01 pode ser violada sem falha | Backend | M2 |
| Branch coverage global abaixo de 70% | Caminhos de erro não exercitados | Backend/Qualidade | M2 |
| Contrato sem lint no CI | Contrato pode divergir da implementação | Backend | M2 |
| Sem e2e, performance e resiliência | Fluxo completo e metas não provados | Plataforma | M2/M6 |
| Sem SAST/dependency scan | Supply chain e injeção não automatizados | Plataforma | M5 |
| Sem diff coverage | Código novo pode nascer sem teste | Qualidade | M2 |

## 8. Definição de pronto de um teste

- [ ] O nome descreve a regra ou invariante, não o método.
- [ ] Há asserção de negócio, não apenas “não lançou exceção”.
- [ ] O teste falha se a regra for removida (verificado por mutação manual ou revisão).
- [ ] Dados são criados pelo próprio teste e não deixam resíduo que afete outro cenário.
- [ ] O comando e a camada estão na matriz de evidências.
- [ ] Requisito/invariante e ameaça relacionada estão referenciados quando aplicável.
- [ ] O resultado aparece em Surefire/JaCoCo/artefato do CI.

## 9. Referências

- [Matriz de evidências por camada](matriz-evidencias.md)
- [Relatório de execução da M1](evidencias/M1-2026-11-06.md)
- [Testes transversais](../../tests/README.md)
- [Requisitos, invariantes e glossário](../requirements/requisitos-invariantes-glossario.md)
- [ADR-002 — Persistência transacional](../adr/ADR-002-persistencia-postgresql.md)
- [Modelo de ameaças](../security/modelo-ameacas.md)
- [Padrão de documentação](../engineering/documentation-standard.md)
