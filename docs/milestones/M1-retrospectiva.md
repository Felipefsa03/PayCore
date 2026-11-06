# Retrospectiva da M1

| Campo | Valor |
| ----- | ----- |
| Status | Implementado |
| Autor | Luis Felipe (com apoio de execução via Gartrol/OpenCode) |
| Data | 2026-11-06 |
| Depende de | [Demonstração da M1](M1-demonstracao.md), [Checklist da M1](M1-checklist.md) |
| Missão | M1 — Fundação arquitetural |

## 1. O que foi entregue

- 4 cards da M1 concluídos como `done`: monorepo/Makefile/documentação, modelo de ameaças +
  ADR-002, estratégia de testes/evidências e demonstração/checklist/retrospectiva.
- 4 commits encadeados sobre a entrega de contas (`56f7730`):
  - `b7a24f6` docs(repo): estrutura, Makefile, padrão e templates
  - `c974197` docs(security): STRIDE + ADR-002 + checkers
  - `1a179b6` docs(quality): estratégia, matriz, relatório e JaCoCo
  - commit final deste card (demonstração, checklist, retrospectiva)
- 24 testes automatizados (12 unitários + 12 de integração), 0 falhas.
- 0 violações Checkstyle em 6 verificações.
- Cobertura: 81,2% linhas / 61,8% branches (Shared 50,0%, Accounts 93,0%, App 83,5%).
- 24 ameaças STRIDE com dono, mitigação e risco residual aceito.
- 22 arquivos Markdown com links válidos; 2 checkers automatizados.

## 2. O que funcionou bem

- **Empilhar as entregas de documentação sobre a entrega de contas** permitiu referenciar a
  API real e os testes existentes, em vez de escrever documentação genérica.
- **Checkers pequenos (`check-docs-links.py`, `check-threat-model.py`,
  `coverage-summary.py`)** transformaram critérios subjetivos (“documentação navegável”,
  “cada ameaça tem dono”) em validações executáveis no CI.
- **JaCoCo no `verify`** deu números concretos e expôs a lacuna de `TransferState` sem
  bloquear o build antes de haver testes.
- **Modo externo de Postgres (`PAYCORE_IT_*`)** desbloqueou a validação completa em um
  ambiente sem Docker, em vez de pular os testes de integração.
- **Escopo claro por card** evitou que documentação, segurança e testes se misturassem em
  uma única mudança inrevisável.

## 3. O que não funcionou / atrapalhou

- **Ausência de Docker e Node no ambiente local** impediu exercitar `make up`, Testcontainers
  e o lint do contrato. A validação usou substitutos documentados, mas a prova completa fica
  para o CI.
- **Ausência de `gh` e token GitHub** impediu criar PRs e disparar o CI automaticamente;
  a entrega depende de ação humana para abrir as PRs empilhadas.
- **`make run` original não funcionava a partir do agregador Maven** por resolução de prefixo
  do plugin Spring Boot; foi corrigido para rodar o jar construído. Isso deveria ter sido
  detectado antes, na validação da M1 anterior.
- **Cobertura de branches abaixo da meta (61,8% vs. 70%)** e `TransferState` com 0/7 linhas
  mostram que os testes atuais favorecem o caminho feliz.
- **Rotas de execução precisaram ser trocadas no banco** (claude-code → opencode) para todos os
  cards, porque a rota original não estava disponível na sessão. O procedimento funcionou, mas
  é operacionalmente frágil e deve ser automatizado ou documentado.

## 4. Decisões tomadas

- PostgreSQL 16 como fonte única da verdade contábil, com `READ COMMITTED` e locks explícitos
  em ordem determinística (ADR-002).
- Ledger append-only, com balanceamento validado no serviço e proteção no banco no M2.
- Idempotência escopada por proprietário, com fingerprint e snapshot imutável.
- Cobertura declarada, mas `jacoco:check` bloqueante apenas a partir do M2.
- Trunk-Based Development com branches tipadas e curtas; PRs empilhados até a base de contas.

## 5. Ações para a M2

| Ação | Dono | Prazo | Critério de conclusão |
| ---- | ---- | ----- | --------------------- |
| Testar `TransferState` e caminhos de erro | Backend | Início da M2 | Shared ≥ 70% linhas |
| Implementar transferência atômica com locks e teste de concorrência | Backend | M2 | 100 requisições simultâneas, 1 efetivação |
| Implementar ledger append-only e teste de balanceamento | Backend | M2 | I-01 provado por teste e constraint |
| Habilitar `jacoco:check` com metas atuais | Qualidade | M2 | Build falha se cobertura cair |
| Automatizar lint do contrato no CI | Backend | M2 | Redocly no workflow |
| Validar `make up` e Testcontainers com Docker | Plataforma | Antes do primeiro PR de M2 | Evidência no relatório |
| Documentar rotação de segredos e TLS | Plataforma/Segurança | M5 | Runbook + teste |
| Automatizar troca de rota de execução ou documentá-la | Plataforma | M2 | Procedimento reproduzível |

## 6. Métricas da M1

| Métrica | Valor |
| ------- | ----- |
| Cards concluídos | 4/4 |
| Commits de entrega | 4 |
| Testes automatizados | 24 |
| Falhas de teste | 0 |
| Violações Checkstyle | 0 |
| Ameaças modeladas | 24 |
| Cobertura de linhas | 81,2% |
| Cobertura de branches | 61,8% |
| Arquivos Markdown validados | 22 |
| Alvos do Makefile | 13 (inclui `coverage-summary`) |

## 7. Referências

- [Demonstração da M1](M1-demonstracao.md)
- [Checklist da M1](M1-checklist.md)
- [Estratégia de testes](../quality/estrategia-de-testes.md)
- [Modelo de ameaças](../security/modelo-ameacas.md)
- [ADR-002](../adr/ADR-002-persistencia-postgresql.md)
