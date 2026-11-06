# Autenticação e autorização de contas

| Campo      | Valor                                                                 |
| ---------- | --------------------------------------------------------------------- |
| Status     | Implementado (RF-01, RF-02, RNF-06)                                   |
| Depende de | [RFC-001](../rfc/RFC-001-visao-e-arquitetura-inicial.md), [Modelo de domínio](modelo-dominio-estados-transferencia.md), [`contracts/openapi/financial-core.yaml`](../../contracts/openapi/financial-core.yaml) |

## Objetivo

Registrar as decisões de autenticação e autorização da API de contas e como
validar manualmente o cenário de acesso cruzado entre dois usuários.

## Autenticação

- A API é stateless e exige `Authorization: Bearer <jwt>` em todas as rotas,
  exceto os endpoints de health/info do Actuator.
- O token é um JWT HS256 com `iss = paycore-lab` e `sub = <ownerId>` (UUID do
  proprietário). O claim `sub` é a única fonte de identidade: o `ownerId` nunca
  vem do corpo ou da URL.
- Tokens sem assinatura válida, expirados, com emissor diferente ou com `sub`
  que não seja UUID recebem `401 unauthenticated`.
- O segredo é configurado por `PAYCORE_AUTH_JWT_SECRET` (mínimo de 32 bytes).
  O valor padrão em `application.yml` é **exclusivo do laboratório local** e
  deve ser substituído em qualquer ambiente compartilhado.
- A emissão de tokens é responsabilidade de um provedor de identidade
  simulado, externo a este serviço. O card de contas valida o token, não o
  emite.

## Autorização por proprietário

| Situação                                        | Resposta                     |
| ----------------------------------------------- | ---------------------------- |
| Conta não existe                                | `404 account_not_found`      |
| Conta existe, mas pertence a outro proprietário | `403 forbidden`              |
| Conta pertence ao proprietário do token         | `200` com a representação    |

Regras equivalentes valem para `GET /accounts/{accountId}` e
`GET /accounts/{accountId}/postings`. O saldo disponível é sempre a projeção
`SUM(créditos) - SUM(débitos)` dos postings, nunca um campo gravado na conta.

## Como validar o acesso cruzado manualmente

1. Suba o Postgres e a aplicação com `make run` (ou `make up` e
   `./mvnw spring-boot:run` em `services/financial-core`).
2. Gere um token para cada proprietário com o segredo de laboratório:

```python
import base64, hashlib, hmac, json, time, sys

def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

secret = "paycore-lab-dev-only-secret-change-me-0123456789abcdef"
owner_id = sys.argv[1]
header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
payload = b64(json.dumps(
    {"sub": owner_id, "iss": "paycore-lab", "exp": int(time.time()) + 3600},
    separators=(",", ":"),
).encode())
signature = b64(hmac.new(secret.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest())
print(f"{header}.{payload}.{signature}")
```

3. Crie uma conta com o token de Alice e tente lê-la com o token de Bob:

```sh
curl -s -X POST localhost:8080/accounts \
  -H "Authorization: Bearer $TOKEN_ALICE" \
  -H "Content-Type: application/json" \
  -d '{"currency":"BRL"}'
# 201 com ownerId = Alice

curl -i localhost:8080/accounts/$ACCOUNT_ID \
  -H "Authorization: Bearer $TOKEN_BOB"
# 403 {"code":"forbidden", ...}

curl -i localhost:8080/accounts/$ACCOUNT_ID
# 401 {"code":"unauthenticated", ...}
```

A prova automatizada do mesmo cenário está em
`app/src/test/.../accounts/AccountApiIntegrationTest.java`.
