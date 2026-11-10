# Risk Engine (FastAPI)

Serviço satélite Python para avaliação de risco de pagamentos (RF-09).

## Executar

```sh
cd services/risk-engine
PYTHONPATH=. uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Testar

```sh
cd services/risk-engine
PYTHONPATH=. pytest
```

## Endpoint principal

`POST /risk/evaluate` recebe uma avaliação de pagamento e retorna `APPROVE`, `REVIEW` ou
`REJECT`, com score, motivos e versão de regras. A mesma entrada com a mesma versão de
regras produz a mesma decisão.
