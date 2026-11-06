# Documentação do PayCore

Índice único da documentação. Comece pelo caminho que corresponde à sua pergunta; cada
documento declara status, data, dependências e missão no cabeçalho.

## Visão e requisitos

| Documento | Conteúdo | Status |
| --------- | -------- | ------ |
| [RFC-001 — Visão e Arquitetura Inicial](rfc/RFC-001-visao-e-arquitetura-inicial.md) | Problema, escopo, alternativas, arquitetura escolhida, riscos e critérios de sucesso | Em revisão |
| [Requisitos, invariantes e glossário](requirements/requisitos-invariantes-glossario.md) | RF-01..RF-12, RNFs mensuráveis, I-01..I-08 e vocabulário de domínio | Em revisão |
| [Modelo de domínio e estados da transferência](architecture/modelo-dominio-estados-transferencia.md) | Entidades, máquina de estados, erros e contrato OpenAPI | Em revisão |
| [Autenticação e autorização de contas](architecture/autenticacao-e-autorizacao-contas.md) | JWT, autorização por proprietário e roteiro de validação cruzada | Implementado |

## Decisões e segurança

| Documento | Conteúdo | Status |
| --------- | -------- | ------ |
| [Índice de ADRs](adr/README.md) | Decisões estruturais e quando criar uma nova | — |
| [Padrão de documentação por milestone](engineering/documentation-standard.md) | Taxonomia, ciclo de vida, templates e evidências | Vigente |

ADR-001 (monólito modular) e ADR-002 (persistência e concorrência) serão publicados na M1.
O modelo de ameaças STRIDE e as mitigações priorizadas também fazem parte do pacote da M1.

## Qualidade e operação

| Documento | Conteúdo | Status |
| --------- | -------- | ------ |
| [Testes transversais](../tests/README.md) | O que pertence ao diretório `tests/` e como executar cada camada | Vigente |
| Estratégia de testes e matriz de evidências | Camadas, comandos, cobertura e evidências por requisito | Planejado (M1) |
| Runbooks operacionais | Procedimentos de incidente e recuperação | Planejado (M5) |

## Milestones

| Documento | Conteúdo | Status |
| --------- | -------- | ------ |
| [Índice de milestones](milestones/README.md) | Pacote mínimo exigido por milestone | Vigente |
| Demonstração, checklist e retrospectiva da M1 | Prova reproduzível da fundação arquitetural | Planejado (M1) |

## Estudos de caso

| Documento | Conteúdo |
| --------- | -------- |
| [Architecture Challenge #01 — Diagnóstico Inicial](case-studies/architecture-challenge-01-diagnostico-inicial.md) | Problema de negócio, perguntas ao CTO, risco e critérios de MVP |

## Templates

- [ADR](templates/adr.md)
- [Pacote de milestone](templates/milestone.md)
- [Runbook](templates/runbook.md)

## Como manter este índice

1. Todo documento novo entra na seção correspondente **na mesma entrega** que o criou.
2. O status da tabela deve refletir o cabeçalho do documento.
3. Documentos substituídos permanecem linkados, marcados como `Substituído`, apontando para
   o substituto.
4. `make docs-check` precisa passar antes da entrega.

O padrão completo está em
[`engineering/documentation-standard.md`](engineering/documentation-standard.md).
