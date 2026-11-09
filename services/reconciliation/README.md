# Reconciliation (paycore/reconciliation)

Serviço PHP/Laravel que importa extratos bancários fictícios em CSV e concilia
cada lançamento com as transações conhecidas do ledger, registrando toda
divergência encontrada sem alterar o ledger (RF-10).

## Requisitos

- PHP 8.3+ (testado em 8.4) e Composer 2.
- SQLite para desenvolvimento/testes (padrão). PostgreSQL é suportado via
  `DB_CONNECTION=pgsql` para rodar contra a mesma instância dos demais serviços.

## Variáveis de ambiente

| Variável | Obrigatória | Descrição |
| --- | --- | --- |
| `RECONCILIATION_API_KEY` | sim | Segredo exigido no cabeçalho `X-Api-Key` em toda rota `/api/*`. |
| `RECONCILIATION_MAX_UPLOAD_KB` | não | Tamanho máximo do CSV enviado (padrão `5120`). |
| `RECONCILIATION_STATEMENTS_DISK` | não | Disco de armazenamento dos extratos (padrão `local`, privado). |

As demais variáveis (`DB_*`, `QUEUE_CONNECTION`, etc.) seguem o padrão do Laravel — veja `.env.example`.

## Executando

```sh
composer install
cp .env.example .env
php artisan key:generate
php artisan migrate --seed   # popula transactions com lançamentos fictícios
php artisan serve
```

O import roda em um `Job` (fila `database` por padrão). Em outro terminal, processe a
fila para o import deixar de ficar `pending`:

```sh
php artisan queue:work
```

Para testar sem subir um worker, defina `QUEUE_CONNECTION=sync` no `.env` — o import roda
de forma síncrona dentro da própria requisição (é o que `phpunit.xml` já faz nos testes).

## API

Toda rota exige o cabeçalho `X-Api-Key: <RECONCILIATION_API_KEY>`.

- `POST /api/statement-imports` — multipart com o campo `statement` (CSV). Responde
  `202` com o resumo do import recém-criado, ou `200` com o import já existente
  quando o conteúdo do arquivo (hash SHA-256) já foi importado antes — reenviar o
  mesmo arquivo nunca reprocessa nem duplica lançamentos.
- `GET /api/statement-imports/{id}` — status e contadores do import
  (`total_rows`, `matched_rows`, `duplicate_rows`, `error_rows`).
- `GET /api/statement-imports/{id}/divergences` — lista paginada das divergências
  detectadas para o import.

### Formato do CSV

Cabeçalho obrigatório: `reference,amount_cents,currency,status,occurred_at`.

```csv
reference,amount_cents,currency,status,occurred_at
TXN-00001,150000,BRL,settled,2026-11-01T10:00:00Z
```

## Conciliação e divergências

Cada entrada válida e não duplicada é comparada por `reference` com a tabela
`transactions` (a visão fictícia do ledger que este serviço concilia; em
produção seria alimentada por uma exportação do `financial-core`). Toda
divergência é persistida em `divergences` com tipo e detalhes, sem jamais
corrigir ou remover dados automaticamente:

| Tipo | Quando ocorre |
| --- | --- |
| `duplicate_in_file` | A mesma `reference` aparece mais de uma vez no arquivo. |
| `mismatch` | A `reference` existe no ledger, mas valor, moeda ou status diferem. |
| `missing_in_ledger` | A `reference` do extrato não existe no ledger. |
| `missing_in_statement` | O ledger tem uma transação no período do extrato que o arquivo não reportou. |
| `invalid_row` | A linha é malformada ou falha validação (campo ausente, valor não numérico, data inválida). |

A resolução de divergências (fluxo de investigação e correção) é um card
separado; este serviço apenas detecta e rastreia.

## Testes

```sh
php artisan test
```

A suíte cobre: importação completa com todos os tipos de divergência,
idempotência ao reimportar o mesmo arquivo, duplicidade dentro de um mesmo
arquivo e rejeição de requisições sem a API key. `QUEUE_CONNECTION=sync` em
`phpunit.xml` faz o job de processamento rodar de forma síncrona nos testes.

## Decisões

- Idempotência é por conteúdo do arquivo (hash SHA-256), não pelo nome: o mesmo
  extrato enviado com nomes diferentes resolve para o mesmo import.
- O arquivo é armazenado no disco privado `local` (`storage/app/private`, fora
  do webroot) com o nome derivado do hash, o que também evita colisão e
  path traversal.
- O import roda em um `Job` (`ProcessStatementImport`) para poder ser
  enfileirado atrás de um worker real em produção; cada linha é persistida
  mesmo quando inválida ou duplicada, preservando trilha de auditoria completa.
- `missing_in_statement` é calculado apenas dentro da janela de datas cobertas
  pelas entradas válidas do próprio import, para não acusar ausência de
  transações fora do período que o extrato pretendia cobrir.
