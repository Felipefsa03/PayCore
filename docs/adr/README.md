# Architecture Decision Records (ADRs)

Registro das decisões arquiteturais estruturais do PayCore. Uma ADR explica **contexto,
alternativas, decisão e consequências** — não é um tutorial nem um resumo de implementação.

| ADR | Título | Status | Missão |
| --- | ------ | ------ | ------ |
| ADR-001 | Monólito modular em Java e fronteiras de extração | Planejado | M1 |
| [ADR-002](ADR-002-persistencia-postgresql.md) | Persistência transacional em PostgreSQL e concorrência | Aceito | M1 |

## Quando criar uma ADR

Crie uma ADR quando a decisão:

- for cara ou arriscada de reverter;
- afetar mais de um módulo, serviço ou time;
- introduzir uma tecnologia, padrão de consistência ou fronteira nova;
- exigir uma compensação consciente (trade-off) que precise sobreviver à rotatividade.

Decisões locais e reversíveis ficam no código, nos testes ou no documento de arquitetura
correspondente. O template está em [`../templates/adr.md`](../templates/adr.md) e o ciclo de
vida dos documentos está em
[`../engineering/documentation-standard.md`](../engineering/documentation-standard.md).
