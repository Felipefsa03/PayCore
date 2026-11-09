import { INestApplication } from '@nestjs/common';
import { DocumentBuilder, OpenAPIObject, SwaggerModule } from '@nestjs/swagger';

export function buildOpenApiDocument(app: INestApplication): OpenAPIObject {
  const config = new DocumentBuilder()
    .setTitle('PayCore Integration API')
    .setDescription(
      'API de integração para parceiros simulados do PayCore. Autenticação por API key ' +
        '(`X-Api-Key`); cada parceiro enxerga apenas as próprias transações e webhooks. ' +
        'Entregas de webhook são assinadas com HMAC-SHA256 e timestamp, com retry, DLQ e ' +
        'reenvio manual auditável.',
    )
    .setVersion('0.1.0')
    .setLicense('UNLICENSED', 'https://example.invalid/license')
    .addServer('http://localhost:3000', 'Ambiente local do laboratório')
    .addApiKey(
      {
        type: 'apiKey',
        in: 'header',
        name: 'X-Api-Key',
        description: 'API key do parceiro simulada.',
      },
      'partnerApiKey',
    )
    .addGlobalParameters({
      name: 'X-Correlation-Id',
      in: 'header',
      required: false,
      description:
        'Identificador de correlação (1-100 caracteres seguros). Gerado quando ausente e ' +
        'devolvido no cabeçalho da resposta.',
      schema: { type: 'string', maxLength: 100 },
    })
    .addTag('Transactions', 'Consulta de transações do parceiro (RF-08).')
    .addTag('Webhooks', 'Cadastro de webhooks do parceiro (RF-11).')
    .addTag(
      'Webhook deliveries',
      'Histórico, tentativas, dead-letter queue e reenvio manual de webhooks (RF-11).',
    )
    .addTag('Health', 'Liveness do serviço.')
    .build();
  return SwaggerModule.createDocument(app, config);
}

export function setupOpenApi(app: INestApplication): OpenAPIObject {
  const document = buildOpenApiDocument(app);
  SwaggerModule.setup('docs', app, document, {
    jsonDocumentUrl: '/openapi.json',
    swaggerOptions: { persistAuthorization: true },
  });
  return document;
}
