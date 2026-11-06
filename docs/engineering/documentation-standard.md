# Padrão de documentação por milestone

| Campo | Valor |
| ----- | ----- |
| Status | Vigente |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-06 |
| Depende de | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md) |
| Missão | M1 — Fundação arquitetural |
| Aplica-se a | Toda documentação em `docs/`, `README.md` e `tests/README.md` |

## Objetivo

Padronizar como o PayCore documenta escopo, decisões, evidências e aprendizados ao longo
das seis milestones. O padrão existe para responder três perguntas sem depender de
memória ou de histórico de conversas:

1. **O que foi prometido?** Escopo, requisitos e critérios de aceite.
2. **O que foi decidido e por quê?** ADRs, RFCs e registros de trade-off.
3. **O que foi provado?** Comandos, relatórios, testes e limitações explícitas.

Documentação não substitui código nem testes; ela torna as decisões e as provas
**descobríveis, revisáveis e estáveis no tempo**.

## 1. Taxonomia

| Pasta | Contém | Não contém |
| ----- | ------ | ---------- |
| `docs/rfc/` | Propostas amplas de visão, escopo e arquitetura, revisadas antes de virar implementação | Decisões pontuais ou atas de reunião |
| `docs/adr/` | Uma decisão estrutural por arquivo, com alternativas e consequências | Tutoriais, changelog ou múltiplas decisões misturadas |
| `docs/architecture/` | Modelos de domínio, C4, fluxos e contratos que descrevem o sistema **como ele é** ou será | Decisões isoladas (essas vão para ADR) |
| `docs/requirements/` | Requisitos rastreáveis, invariantes, glossário e critérios de aceite | Planejamento de sprint |
| `docs/security/` | Modelo de ameaças, controles, riscos residuais e política de segredos | Credenciais, chaves ou dados sensíveis reais |
| `docs/quality/` | Estratégia de testes, matriz de evidências, cobertura e relatórios | Resultados brutos de terminal |
| `docs/milestones/` | Escopo, demonstração, checklist e retrospectiva de cada milestone | Documentação permanente de arquitetura |
| `docs/runbooks/` | Procedimentos operacionais e de incidente, passo a passo | Explicação conceitual longa |
| `docs/case-studies/` | Narrativas de problema, diagnóstico e decisão para portfólio | Duplicação da documentação técnica |
| `docs/templates/` | Modelos reutilizáveis de ADR, milestone e runbook | Conteúdo específico do projeto |

Regra prática: **se a resposta for “por que escolhemos X?”, é ADR; se for “como o sistema
funciona?”, é arquitetura; se for “como provamos que funciona?”, é qualidade; se for “o que
entregamos nesta milestone?”, é milestone.**

## 2. Nomenclatura

- Arquivos em `kebab-case`, sem acentos e sem espaços.
- RFC: `RFC-NNN-titulo-curto.md` (ex.: `RFC-001-visao-e-arquitetura-inicial.md`).
- ADR: `ADR-NNN-titulo-curto.md` (ex.: `ADR-002-persistencia-postgresql.md`).
- Milestone: `M<N>-tipo.md` (ex.: `M1-demonstracao.md`, `M1-checklist.md`,
  `M1-retrospectiva.md`).
- Runbook: `runbook-<evento>.md` (ex.: `runbook-falha-postgresql.md`).
- Datas em `AAAA-MM-DD`; valores monetários em centavos; identificadores de código em
  inglês e texto em português.

## 3. Cabeçalho obrigatório

Todo documento em `docs/` (exceto índices e templates) começa com uma tabela de metadados:

```markdown
| Campo | Valor |
| ----- | ----- |
| Status | Em revisão |
| Autor | Nome (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-06 |
| Depende de | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md) |
| Missão | M1 — Fundação arquitetural |
```

- **Status**: um de `Rascunho`, `Em revisão`, `Aceito`, `Implementado`, `Vigente`,
  `Substituído`, `Obsoleto`.
- **Data**: última mudança relevante de conteúdo, não a data de criação do arquivo.
- **Depende de**: links para documentos dos quais o conteúdo não pode ser entendido
  isoladamente. Use `—` quando não houver dependência.
- **Missão**: milestone ou atividade contínua à qual o documento pertence.
- Documentos de decisão adicionam `Decisão vinculada` ou `Substitui` quando aplicável.

## 4. Ciclo de vida

```text
Rascunho ──► Em revisão ──► Aceito ──► Implementado
                  │                         │
                  └────────► Substituído ◄──┘
                                  │
                                  ▼
                              Obsoleto
```

- **Rascunho**: conteúdo incompleto; não deve ser citado como decisão.
- **Em revisão**: pronto para revisão humana; mudanças estruturais ainda são esperadas.
- **Aceito**: decisão aprovada; implementação pode começar.
- **Implementado/Vigente**: o documento descreve o que existe e é mantido junto do código.
- **Substituído**: mantido para histórico, com link para o substituto.
- **Obsoleto**: não deve mais ser usado; mantido apenas se houver valor histórico.

Uma mudança de status é uma mudança de conteúdo: atualize o cabeçalho e o índice.

## 5. ADRs

Crie uma ADR quando a decisão for estrutural, cross-cutting ou cara de reverter. A ADR
deve conter, nesta ordem:

1. **Contexto** — forças em jogo, restrições e o que acontece se nada for decidido.
2. **Alternativas consideradas** — pelo menos duas, com prós e contras.
3. **Decisão** — afirmação no presente, específica o bastante para ser verificável.
4. **Consequências** — positivas, negativas e riscos aceitos.
5. **Critérios de revisão** — que evidência faria a decisão ser revista.
6. **Referências** — requisitos, RFCs, código, testes e issues.

Uma ADR não é editada para refletir uma decisão diferente: crie outra ADR e marque a
anterior como `Substituído`. Correções factuais e links podem ser atualizados.

## 6. Pacote de milestone

Cada milestone publica, no mínimo:

| Documento | Conteúdo obrigatório |
| --------- | -------------------- |
| Escopo | Objetivos, itens dentro/fora, dependências e riscos iniciais |
| Demonstração | Pré-requisitos, comandos, resultado esperado e limitações do ambiente |
| Checklist | Critério, status, evidência linkada e responsável pela validação |
| Retrospectiva | O que funcionou, o que não funcionou, decisões e ações com dono e prazo |
| ADRs e ameaças | Decisões estruturais e riscos residuais aceitos na milestone |

O checklist só pode ser marcado como 100% quando **cada item tiver evidência reproduzível**
(comando, teste, relatório ou link de CI). “Funciona na minha máquina” não é evidência.

## 7. Evidências

Evidência boa é curta, reproduzível e aponta para a fonte:

- comando exato e resultado resumido (ex.: `make test` → 24 testes, 0 falhas);
- link para o arquivo de teste, relatório Surefire/JaCoCo ou artefato de CI;
- commit e branch que contêm a mudança;
- captura de tela apenas quando o resultado for visual e não reproduzível por comando.

Não inclua:

- logs brutos longos, transcrições de chat ou dumps de terminal;
- segredos, tokens, senhas, URLs assinadas ou dados pessoais;
- resultados sem ambiente, versão ou comando que os produziu.

Resultados de performance e resiliência sempre declaram ambiente, volume, concorrência,
taxa de erro e metodologia (RFC-001 §3.2).

## 8. Diagramas

- Use **Mermaid** em bloco cercado por ` ```mermaid ` para diagramas versionáveis.
- Um diagrama por preocupação: contexto, containers, componentes, sequência ou estados.
- Prefixe o diagrama com uma frase que diga o que ele responde.
- Em C4, mantenha a notação: pessoa, sistema, container, componente; nomeie fronteiras.
- Não cole imagens binárias quando um diagrama textual resolver; se usar imagem, guarde a
  fonte editável em `docs/assets/` e referencie no texto.

## 9. Revisão e definição de pronto

Um documento está pronto quando:

- [ ] o cabeçalho está completo e o status é coerente com o conteúdo;
- [ ] links relativos funcionam (`make docs-check`);
- [ ] está indexado em [`docs/README.md`](../README.md);
- [ ] afirmações estruturais apontam para ADR, requisito ou código;
- [ ] evidências são reproduzíveis e não contêm segredos;
- [ ] pelo menos uma pessoa revisou documentos de decisão, segurança ou milestone;
- [ ] documentos substituídos apontam para o substituto.

Mudanças de código que alteram comportamento documentado atualizam a documentação na
**mesma entrega**. Documentação não é uma fase posterior.

## 10. Anti-padrões

- Documento sem dono, sem data ou com status “feito” sem evidência.
- ADR que lista apenas a opção escolhida e esconde as alternativas.
- Checklist marcado sem link para teste, comando ou relatório.
- README que duplica o RFC e vira uma segunda fonte da verdade desatualizada.
- Diagrama sem legenda ou que mistura contexto, containers e classes na mesma figura.
- Runbook que depende de conhecimento tácito (“rodar o script de sempre”).
- Segredo em exemplo, mesmo que “falso”.

## Referências

- [RFC-001 — Visão e Arquitetura Inicial](../rfc/RFC-001-visao-e-arquitetura-inicial.md)
- [Requisitos, invariantes e glossário](../requirements/requisitos-invariantes-glossario.md)
- [Índice de ADRs](../adr/README.md)
- [Template de ADR](../templates/adr.md)
- [Template de milestone](../templates/milestone.md)
- [Template de runbook](../templates/runbook.md)
