import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { ALPHA, BETA } from '../helpers/partners';
import { StubFinancialCore } from '../helpers/stub-financial-core';
import { createTestApp, resetDatabase, resetRateLimits } from '../helpers/test-app';

const ALPHA_TRANSACTION = 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa';
const BETA_TRANSACTION = 'bbbbbbbb-2222-4222-8222-bbbbbbbbbbbb';
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

describe('Transactions e isolamento entre parceiros', () => {
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
    stub.transfers.clear();
    stub.requests.length = 0;
  });

  it('consulta a própria transação e propaga a correlação ao núcleo', async () => {
    stub.seed(ALPHA_TRANSACTION, ALPHA.ownerId);

    const response = await request(app.getHttpServer())
      .get(`/v1/transactions/${ALPHA_TRANSACTION}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .set('X-Correlation-Id', 'corr-alpha-001')
      .expect(200);

    expect(response.headers['x-correlation-id']).toBe('corr-alpha-001');
    expect(response.body).toMatchObject({
      id: ALPHA_TRANSACTION,
      state: 'COMPLETED',
      amount: 400,
      currency: 'BRL',
      failureReason: null,
    });
    expect(stub.requests).toHaveLength(1);
    expect(stub.requests[0].sub).toBe(ALPHA.ownerId);
    expect(stub.requests[0].correlationId).toBe('corr-alpha-001');
  });

  it('não expõe transação de outro parceiro', async () => {
    stub.seed(BETA_TRANSACTION, BETA.ownerId);

    const response = await request(app.getHttpServer())
      .get(`/v1/transactions/${BETA_TRANSACTION}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(404);

    expect(response.body.code).toBe('transaction_not_found');
    expect(response.body.correlationId).toMatch(UUID_PATTERN);
    expect(stub.requests[0].sub).toBe(ALPHA.ownerId);
  });

  it('gera correlação quando o cliente não envia', async () => {
    stub.seed(ALPHA_TRANSACTION, ALPHA.ownerId);

    const response = await request(app.getHttpServer())
      .get(`/v1/transactions/${ALPHA_TRANSACTION}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);

    const correlationId = response.headers['x-correlation-id'];
    expect(correlationId).toMatch(UUID_PATTERN);
    expect(stub.requests[0].correlationId).toBe(correlationId);
  });

  it('descarta correlação recebida fora do formato permitido', async () => {
    stub.seed(ALPHA_TRANSACTION, ALPHA.ownerId);

    const response = await request(app.getHttpServer())
      .get(`/v1/transactions/${ALPHA_TRANSACTION}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .set('X-Correlation-Id', 'a'.repeat(150))
      .expect(200);

    expect(response.headers['x-correlation-id']).toMatch(UUID_PATTERN);
    expect(response.headers['x-correlation-id']).not.toBe('a'.repeat(150));
  });

  it('recusa credencial ausente ou desconhecida', async () => {
    await request(app.getHttpServer())
      .get(`/v1/transactions/${ALPHA_TRANSACTION}`)
      .expect(401)
      .expect((response) => expect(response.body.code).toBe('unauthenticated'));

    await request(app.getHttpServer())
      .get(`/v1/transactions/${ALPHA_TRANSACTION}`)
      .set('X-Api-Key', 'chave-que-nao-existe')
      .expect(401)
      .expect((response) => expect(response.body.code).toBe('unauthenticated'));
  });

  it('valida o UUID do path', async () => {
    const response = await request(app.getHttpServer())
      .get('/v1/transactions/nao-e-uuid')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(400);

    expect(response.body.code).toBe('validation_error');
    expect(response.body.correlationId).toMatch(UUID_PATTERN);
  });

  it('responde 503 quando o núcleo financeiro está indisponível', async () => {
    await stub.stop();
    try {
      const response = await request(app.getHttpServer())
        .get(`/v1/transactions/${ALPHA_TRANSACTION}`)
        .set('X-Api-Key', ALPHA.apiKey)
        .expect(503);
      expect(response.body.code).toBe('financial_core_unavailable');
    } finally {
      await stub.start(stub.boundPort);
    }
  });

  it('health é público e não exige API key', async () => {
    await request(app.getHttpServer())
      .get('/health')
      .expect(200)
      .expect((response) =>
        expect(response.body).toEqual({ status: 'ok', service: 'integration-api' }),
      );
  });
});
