# Runbook — Nome do evento

| Campo | Valor |
| ----- | ----- |
| Status | Rascunho |
| Autor | Nome (com apoio de execução via Gartrol/OpenCode) |
| Data | AAAA-MM-DD |
| Severidade | SEV-1 / SEV-2 / SEV-3 |
| Serviço | ... |
| Última execução | AAAA-MM-DD (ambiente, resultado) |

## Sintoma

Como o operador percebe o problema (alerta, erro, latência, fila parada).

## Impacto

O que o cliente sente e quais invariantes ficam em risco.

## Diagnóstico

1. Comando/consulta de verificação.
2. O que cada resultado significa.
3. Como distinguir este evento de um parecido.

## Mitigação

Passo a passo, com comandos copiáveis e critério de sucesso por etapa. Inclua rollback.

## Comunicação

Quem avisar, em qual canal, com qual frequência e o que não prometer.

## Encerramento

Critério objetivo para declarar resolvido, evidências a anexar e follow-ups obrigatórios.

## Referências

- Dashboards, alertas, ADRs e testes relacionados.
