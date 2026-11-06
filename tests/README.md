# Testes transversais

Este diretório guarda artefatos de teste que **atravessam serviços ou linguagens**. Testes
unitários e de integração de um serviço vivem ao lado do código desse serviço.

## O que pertence aqui

| Caminho | Conteúdo | Status |
| ------- | -------- | ------ |
| `tests/contract/` | Validação de contratos OpenAPI e compatibilidade entre consumidores | Planejado (M1/M2) |
| `tests/e2e/` | Fluxos completos contra o ambiente local composto | Planejado (M2) |
| `tests/performance/` | Cenários k6/Gatling com ambiente, carga e metodologia declarados | Planejado (M2) |
| `tests/resilience/` | Experimentos de falha, reinício, replay e concorrência | Planejado (M6) |

Enquanto um diretório não existir, o item está apenas planejado — não crie pastas vazias
sem um teste real para colocar nelas.

## Onde estão os testes hoje

| Camada | Local | Comando |
| ------ | ----- | ------- |
| Unitário | `services/financial-core/*/src/test` | `make test-unit` |
| Integração com Postgres real | `services/financial-core/app/src/test` | `make test-integration` |
| Gate completo (CI) | Todo o monorepo | `make test` |
| Contrato OpenAPI | `contracts/openapi/financial-core.yaml` | `npx @redocly/cli lint ...` |

Os testes de integração usam Testcontainers por padrão. Sem Docker, defina
`PAYCORE_IT_JDBC_URL`, `PAYCORE_IT_DB_USER` e `PAYCORE_IT_DB_PASSWORD` para reutilizar um
PostgreSQL local. O banco é migrado pelo Flyway antes de cada cenário.

## Regras

- Um teste deve falhar por um motivo de negócio ou de contrato, não por ordem de execução.
- Testes de integração não dependem de dados deixados por execuções anteriores.
- Evidência de teste cita comando, ambiente e resultado; logs brutos ficam no CI.
- Novo requisito P0 entra com teste automatizado na mesma entrega.
- Teste que prova uma invariante referencia o ID da invariante (ex.: `I-05`) no nome ou na
  descrição.

A estratégia completa, com matriz requisito → camada → evidência, será publicada em
`docs/quality/` na M1.
