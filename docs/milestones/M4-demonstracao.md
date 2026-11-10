# M4 — risco e conciliação

## Reprodução

Use um PostgreSQL descartável e exclusivo. Defina DATABASE_URL (JDBC), DATABASE_USER, DATABASE_PASSWORD e PAYCORE_INTEGRATION_DB_URL (URL PostgreSQL do NestJS). Prepare Java com `make build`, NestJS com `make integration-api-install` e `npm run build` em services/integration-api, e PHP com `composer install` em services/reconciliation. Instale services/risk-engine/requirements.txt em um ambiente virtual Python. Na raiz, execute `python scripts/demo-m4.py` com esse ambiente ativo.

O script sobe quatro processos locais, usa uma base SQLite temporária para conciliação e encerra os processos ao sair. Descarte o PostgreSQL após o teste. No núcleo financeiro, a conta de laboratório admite descoberto. O script cria o pagamento pela API Java. A API de parceiros NestJS consulta a transação autenticada, usada pelo comando Laravel `reconciliation:sync` como referência do extrato.

## Incidente executado

A transferência e1659290-6ec4-4f7d-9ecf-02d65e791181 movimentou 1.000 centavos. O extrato adulterado no laboratório declarou 1.100. A importação detectou mismatch e recebeu investigação e resolução com motivo e referência do operador. O teste comparou os postings das duas contas antes e depois. A resolução levou 0,143 s a partir da preparação do extrato na execução automatizada. O journal foi 5cf8194b-3ff4-4b22-b59b-aed7b1cf8ad3 e a correlação bed8acc4-cf11-48bf-881f-5e3a95855121.

## Retrospectiva

Os testes separados passaram enquanto o primeiro pagamento real falhou. A tentativa de upgrade HTTP do cliente Java chegou ao Uvicorn como uma requisição inválida. Fixar HTTP/1.1 nessa fronteira permitiu o contrato real. O cenário agora exige aprovação explícita e confirma que REVIEW e indisponibilidade impedem movimentação. Repetições do pagamento e do upload reutilizam os identificadores.

O relatório filtra pela data de detecção em UTC, incluindo as duas datas informadas. A referência de operador é declarada por um cliente autenticado pela chave interna do serviço. O cliente calcula idade e frequência a partir da conta e das transferências persistidas. Geografia e confiança do dispositivo são simuladas no laboratório. A demonstração usa fila síncrona e o transporte local de outbox. Kafka, filas assíncronas e operação distribuída exigem evidências próprias.
