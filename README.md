# PayCore

Plataforma financeira distribuída simulada, construída como projeto de portfólio de
engenharia de software. O núcleo atual é um **monólito modular em Java/Spring Boot** com
PostgreSQL como fonte única da verdade contábil; serviços satélites poliglotas entram nas
milestones seguintes, apenas onde a tecnologia resolve um problema real.

> Estado atual: **M3 — Mensageria e integrações (em andamento)**. Contas, transferências,
> ledger, outbox transacional, eventos Kafka versionados e consumidor idempotente já estão
> implementados. A Integration API em NestJS atende parceiros simulados; webhooks assinados e
> a demo do milestone entram nos cards seguintes.

## Por onde começar

| Quero... | Leia |
| -------- | ---- |
| Entender o produto e as decisões arquiteturais | [`docs/rfc/RFC-001`](docs/rfc/RFC-001-visao-e-arquitetura-inicial.md) |
| Ver requisitos, invariantes e glossário | [`docs/requirements`](docs/requirements/requisitos-invariantes-glossario.md) |
| Entender contas, transferências e estados | [`docs/architecture/modelo-dominio-estados-transferencia.md`](docs/architecture/modelo-dominio-estados-transferencia.md) |
| Entender autenticação e autorização | [`docs/architecture/autenticacao-e-autorizacao-contas.md`](docs/architecture/autenticacao-e-autorizacao-contas.md) |
| Ver a decisão de persistência e concorrência | [`docs/adr/ADR-002-persistencia-postgresql.md`](docs/adr/ADR-002-persistencia-postgresql.md) |
| Consultar ameaças, mitigações e riscos aceitos | [`docs/security/modelo-ameacas.md`](docs/security/modelo-ameacas.md) |
| Seguir o padrão de documentação | [`docs/engineering/documentation-standard.md`](docs/engineering/documentation-standard.md) |
| Navegar por toda a documentação | [`docs/README.md`](docs/README.md) |

## Estrutura do monorepo

```text
.
├── contracts/            # Contratos públicos (OpenAPI) e artefatos gerados
├── docs/                 # RFCs, ADRs, arquitetura, requisitos, qualidade e milestones
├── infrastructure/       # Docker Compose e, nas próximas milestones, IaC
├── scripts/              # Automação de apoio ao repositório
├── services/             # Serviços executáveis
│   ├── financial-core/   # Monólito modular Java (shared, accounts, ledger, payments, app)
│   └── integration-api/  # Borda TypeScript/NestJS para parceiros e webhooks
├── tests/                # Testes transversais: contrato, e2e e desempenho
├── Makefile              # Entrada única para as tarefas do laboratório
└── README.md
```

A fronteira de cada módulo Java é intencional: ela ensaia a extração futura de serviços
sem introduzir consistência distribuída no núcleo contábil (RFC-001 §6–§7).

## Pré-requisitos

- **Java 21** (o build usa o Maven Wrapper; não é necessário instalar Maven).
- **Node.js 22+** para a Integration API em `services/integration-api`.
- **Docker com Compose v2** para subir o PostgreSQL local com `make up`.
- **Python 3** apenas para `make docs-check`.
- Alternativa sem Docker: aponte os testes de integração para um PostgreSQL existente com
  `PAYCORE_IT_JDBC_URL`, `PAYCORE_IT_DB_USER` e `PAYCORE_IT_DB_PASSWORD`.

## Comandos principais

```sh
make help          # lista os alvos disponíveis
make up            # sobe o PostgreSQL local (Docker Compose)
make test          # gate completo: compilação, Checkstyle, testes unitários e de integração
make integration-api-check  # typecheck, testes, OpenAPI e build da Integration API
make run           # inicia a API em http://localhost:8080 (requer PostgreSQL)
make docs-check         # valida links relativos da documentação
make security-check     # valida dono, mitigação e risco residual do modelo de ameaças
make coverage-summary   # resume a cobertura JaCoCo por módulo
make down          # derruba o PostgreSQL local
```

O alvo `make test` é o mesmo gate executado no CI. Para rodar apenas uma camada:

```sh
make test-unit         # testes rápidos, sem banco
make test-integration  # testes com Postgres real (Testcontainers ou banco externo)
make lint              # Checkstyle
```

## O que já funciona

- `POST /accounts` cria uma conta para o proprietário do token.
- `GET /accounts/{id}` e `GET /accounts/{id}/postings` só retornam dados do proprietário.
- Erros seguem o envelope estável de [`contracts/openapi/financial-core.yaml`](contracts/openapi/financial-core.yaml).
- O contrato OpenAPI é validado por lint e gera tipos TypeScript reproduzíveis.

A **Integration API** (`services/integration-api`) expõe:

- `GET /v1/transactions/{id}` — parceiro consulta a própria transação, com JWT de curta
  duração cunhado para o seu `ownerId` e correlação propagada ao núcleo;
- `POST/GET/DELETE /v1/webhooks` — cadastro, listagem e remoção de webhooks por parceiro,
  com segredo HMAC devolvido apenas na criação;
- autenticação por API key, validação de DTOs, rate limiting por parceiro e OpenAPI em
  `contracts/openapi/integration-api.yaml` (`/docs` em execução).

O roteiro completo de milestones está no RFC-001 e nos documentos de milestone em
[`docs/milestones/`](docs/milestones/README.md).

## Convenções de contribuição

- Commits e títulos de pull request: `type(scope): imperative summary`.
- Trunk-Based Development: `main` é o único tronco; branches são curtas e tipadas.
- Toda decisão estrutural nova vira ADR em [`docs/adr/`](docs/adr/README.md).
- Documentação e código que se referem à mesma decisão entram na mesma entrega.
- Segredos nunca entram no repositório; use variáveis de ambiente.

Detalhes em [`docs/engineering/documentation-standard.md`](docs/engineering/documentation-standard.md)
e em [`memory.md`](memory.md).
