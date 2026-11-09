# Reconciliação: importação idempotente de extratos CSV

| Campo      | Valor                                                     |
| ---------- | ---------------------------------------------------------- |
| Status     | Implementado                                                |
| Autor      | Luis Felipe (com apoio de execução via Gartrol/OpenCode)    |
| Data       | 2026-11-09                                                  |
| Serviço    | [`services/reconciliation`](../../services/reconciliation) |
| Missão     | M4 · Risco e conciliação (semanas 13–16)                    |

## Objetivo

Importar extratos bancários fictícios em CSV e conciliar cada lançamento com o
ledger sem jamais duplicar o efeito de um reenvio e sem corrigir divergências
silenciosamente (RF-10).

## 1. Por que um serviço PHP/Laravel separado

Conciliação é um domínio de borda: lê um arquivo externo, roda em lote/job e
não participa da transação financeira. Isolá-la em `services/reconciliation`
(PHP/Laravel) mantém o núcleo contábil Java livre de parsing de arquivo e
permite evoluir o formato de extrato sem tocar no `financial-core` (RFC-001 §7,
decisão 6 — serviços satélites entram pelas bordas).

A tabela `transactions` deste serviço é uma visão fictícia do ledger, seedada
localmente. Não há chamada síncrona ao `financial-core`; a integração real é
um gatilho explícito fora do escopo deste card.

## 2. Fluxo

```text
POST /api/statement-imports (multipart: statement)
        │
        ▼
hash = sha256(conteúdo do arquivo)
        │
   já existe import com esse hash?
        │                    │
       sim                   não
        │                    │
   200 + import         grava arquivo em disco privado (nome = hash)
   existente             cria StatementImport (status=pending)
   (não reprocessa)      dispatch ProcessStatementImport
                               │
                               ▼
                    202 + import criado
```

`ProcessStatementImport` roda em `QUEUE_CONNECTION=sync` nos testes e pode
rodar atrás de um worker real (`database`/`redis`) em produção sem mudar o
código do job.

## 3. Idempotência

A chave de idempotência é o **hash SHA-256 do conteúdo do arquivo**, não o
nome. `statement_imports.file_hash` é `unique`; reenviar os mesmos bytes — com
qualquer nome de arquivo — devolve o import já processado em vez de
reprocessar. Uma corrida entre dois uploads simultâneos do mesmo conteúdo é
resolvida pela constraint única do banco: quem perde a corrida recebe o
resultado de quem a venceu.

Dentro de um mesmo arquivo, uma `reference` repetida é uma duplicidade
distinta: a primeira ocorrência participa da conciliação, as seguintes são
gravadas (para auditoria) e geram uma divergência `duplicate_in_file`, mas não
são comparadas contra o ledger nem contam como conciliadas.

## 4. Classificação de divergências

Cada entrada válida e não duplicada é comparada por `reference` contra
`transactions`. Nada é corrigido automaticamente — toda divergência é uma
linha em `divergences`:

| Tipo                    | Gatilho                                                              |
| ------------------------ | --------------------------------------------------------------------- |
| `duplicate_in_file`      | `reference` repetida no mesmo arquivo.                                |
| `mismatch`               | `reference` existe no ledger, mas valor, moeda ou status diferem.     |
| `missing_in_ledger`      | `reference` do extrato não existe no ledger.                          |
| `missing_in_statement`   | Transação do ledger dentro do período do extrato que o arquivo não reportou. |
| `invalid_row`            | Linha malformada ou com campo ausente/inválido.                       |

`missing_in_statement` é calculado após o loop principal, usando apenas a
janela `[min(occurred_at), max(occurred_at)]` das entradas válidas e não
duplicadas do próprio import — evitar isso faria o serviço acusar ausência de
transações fora do período que o extrato pretendia cobrir.

## 5. Armazenamento e segurança

- O CSV é validado por extensão e MIME type e salvo no disco `local`
  (`storage/app/private`, fora do webroot), com o nome derivado do hash —
  evita colisão, sobrescrita e path traversal via nome de arquivo do cliente.
- Toda rota exige o cabeçalho `X-Api-Key`, comparado em tempo constante
  (`hash_equals`).

## 6. Evidências

- `StatementImportTest::it_imports_a_statement_and_classifies_every_divergence`:
  um único arquivo produz os cinco tipos de divergência.
- `StatementImportTest::reimporting_the_same_file_is_idempotent`: mesmo
  arquivo duas vezes → mesmo import, nenhuma divergência duplicada.
- `StatementImportTest::a_file_with_an_internal_duplicate_flags_it_without_double_counting_matches`:
  referência repetida no arquivo não conta como conciliada mais de uma vez.
- `StatementImportTest::it_rejects_requests_without_a_valid_api_key`.
- `CsvStatementParserTest`: linhas malformadas e cabeçalho ausente.

## 7. Próximos passos (fora deste card)

- Relatório de conciliação por período e fluxo de investigação/resolução de
  divergências.
- Incidente simulado de divergência contábil.
- Integração real com a exportação do `financial-core` no lugar da seed de
  `transactions`.
