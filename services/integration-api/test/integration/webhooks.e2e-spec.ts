import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { buildOpenApiDocument } from '../../src/openapi/openapi';
import { ALPHA, BETA, LIMITED } from '../helpers/partners';
import { StubFinancialCore } from '../helpers/stub-financial-core';
import { createTestApp, resetDatabase, resetRateLimits } from '../helpers/test-app';

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

describe('Webhooks, validação, rate limit e OpenAPI', () => {
  let app: INestApplication;
  let stub: StubFinancialCore;

  beforeAll(async () => {
    stub = new StubFinancialCore();
    await stub.start();
    app = await createTestApp(stub.baseUrl);
  });

  afterAll(async () => {
    await app.close();
    await stub.stop();
  });

  beforeEach(async () => {
    await resetDatabase(app);
    resetRateLimits(app);
  });

  it('cadastra webhook devolvendo o segredo apenas na criação', async () => {
    const created = await request(app.getHttpServer())
      .post('/v1/webhooks')
      .set('X-Api-Key', ALPHA.apiKey)
      .send({
        url: 'https://parceiro.example/webhooks/paycore',
        eventTypes: ['payment.completed', 'payment.rejected'],
        description: 'Webhook do Alpha',
      })
      .expect(201);

    expect(created.body.id).toMatch(UUID_PATTERN);
    expect(created.body.secret).toMatch(/^whsec_[A-Za-z0-9_-]{40,}$/);
    expect(created.body.eventTypes).toEqual(['payment.completed', 'payment.rejected']);
    expect(created.body.active).toBe(true);

    const listed = await request(app.getHttpServer())
      .get('/v1/webhooks')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);

    expect(listed.body).toHaveLength(1);
    expect(listed.body[0].id).toBe(created.body.id);
    expect(listed.body[0]).not.toHaveProperty('secret');
  });

  it('isola listagem e remoção por parceiro', async () => {
    const created = await request(app.getHttpServer())
      .post('/v1/webhooks')
      .set('X-Api-Key', ALPHA.apiKey)
      .send({ url: 'https://alpha.example/hooks', eventTypes: ['payment.completed'] })
      .expect(201);

    const betaList = await request(app.getHttpServer())
      .get('/v1/webhooks')
      .set('X-Api-Key', BETA.apiKey)
      .expect(200);
    expect(betaList.body).toEqual([]);

    await request(app.getHttpServer())
      .delete(`/v1/webhooks/${created.body.id}`)
      .set('X-Api-Key', BETA.apiKey)
      .expect(404)
      .expect((response) => expect(response.body.code).toBe('webhook_not_found'));

    await request(app.getHttpServer())
      .delete(`/v1/webhooks/${created.body.id}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(204);

    const alphaList = await request(app.getHttpServer())
      .get('/v1/webhooks')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(alphaList.body).toEqual([]);
  });

  it('valida DTOs e rejeita campos desconhecidos', async () => {
    const cases: Array<Record<string, unknown>> = [
      { url: 'ftp://parceiro.example/hooks', eventTypes: ['payment.completed'] },
      { url: 'https://user:pass@parceiro.example/hooks', eventTypes: ['payment.completed'] },
      { url: 'https://parceiro.example/hooks', eventTypes: [] },
      { url: 'https://parceiro.example/hooks', eventTypes: ['payment.created'] },
      {
        url: 'https://parceiro.example/hooks',
        eventTypes: ['payment.completed'],
        campoDesconhecido: true,
      },
    ];

    for (const body of cases) {
      const response = await request(app.getHttpServer())
        .post('/v1/webhooks')
        .set('X-Api-Key', ALPHA.apiKey)
        .send(body)
        .expect(400);
      expect(response.body.code).toBe('validation_error');
      expect(response.body.correlationId).toMatch(UUID_PATTERN);
      expect(Array.isArray(response.body.details.fields)).toBe(true);
    }
  });

  it('aplica rate limit por parceiro e devolve Retry-After', async () => {
    await request(app.getHttpServer())
      .get('/v1/webhooks')
      .set('X-Api-Key', LIMITED.apiKey)
      .expect(200);
    await request(app.getHttpServer())
      .get('/v1/webhooks')
      .set('X-Api-Key', LIMITED.apiKey)
      .expect(200);

    const blocked = await request(app.getHttpServer())
      .get('/v1/webhooks')
      .set('X-Api-Key', LIMITED.apiKey)
      .expect(429);

    expect(blocked.body.code).toBe('rate_limited');
    expect(Number(blocked.headers['retry-after'])).toBeGreaterThan(0);
    expect(blocked.body.details.retryAfterSeconds).toBeGreaterThan(0);
  });

  it('publica OpenAPI com autenticação, correlação e rotas', () => {
    const document = buildOpenApiDocument(app);
    expect(document.components?.securitySchemes?.partnerApiKey).toMatchObject({
      type: 'apiKey',
      in: 'header',
      name: 'X-Api-Key',
    });
    expect(Object.keys(document.paths)).toEqual(
      expect.arrayContaining([
        '/v1/transactions/{transactionId}',
        '/v1/webhooks',
        '/v1/webhooks/{webhookId}',
        '/v1/webhook-deliveries',
        '/v1/webhook-deliveries/{deliveryId}',
        '/v1/webhook-deliveries/{deliveryId}/resend',
        '/health',
      ]),
    );
    expect(document.paths['/v1/transactions/{transactionId}']?.get?.tags).toContain(
      'Transactions',
    );
    expect(document.paths['/v1/webhooks']?.post?.tags).toContain('Webhooks');
    expect(document.paths['/v1/webhook-deliveries']?.get?.tags).toContain(
      'Webhook deliveries',
    );
    expect(
      document.paths['/v1/webhook-deliveries/{deliveryId}/resend']?.post?.tags,
    ).toContain('Webhook deliveries');
  });
});
