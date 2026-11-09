import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { WebhookDeliveryWorker } from '../../src/webhooks/delivery/webhook-delivery.worker';
import { isBlockedAddress } from '../../src/webhooks/delivery/webhook-destination.guard';
import {
  EVENT_TOPICS,
  InvalidWebhookEventError,
  WebhookEventIntakeService,
} from '../../src/webhooks/delivery/webhook-event.intake';
import { ALPHA, BETA } from '../helpers/partners';
import { paymentCompletedEvent, paymentRejectedEvent } from '../helpers/payment-event';
import { StubFinancialCore } from '../helpers/stub-financial-core';
import { StubWebhookReceiver } from '../helpers/stub-webhook-receiver';
import { createTestApp, resetDatabase, resetRateLimits } from '../helpers/test-app';

describe('Entregas de webhooks: HMAC, retry, DLQ e reenvio', () => {
  let app: INestApplication;
  let financialCore: StubFinancialCore;
  let receiver: StubWebhookReceiver;
  let intake: WebhookEventIntakeService;
  let worker: WebhookDeliveryWorker;

  beforeAll(async () => {
    financialCore = new StubFinancialCore();
    await financialCore.start();
    app = await createTestApp(financialCore.baseUrl);
    intake = app.get(WebhookEventIntakeService);
    worker = app.get(WebhookDeliveryWorker);
  });

  afterAll(async () => {
    await app.close();
    await financialCore.stop();
  });

  beforeEach(async () => {
    await resetDatabase(app);
    resetRateLimits(app);
    receiver = new StubWebhookReceiver();
    await receiver.start();
  });

  afterEach(async () => {
    await receiver.stop();
  });

  async function createWebhook(
    eventTypes: string[] = ['payment.completed', 'payment.rejected'],
    partner = ALPHA,
    path = '/hooks/alpha',
  ): Promise<{ id: string; secret: string }> {
    const created = await request(app.getHttpServer())
      .post('/v1/webhooks')
      .set('X-Api-Key', partner.apiKey)
      .send({ url: receiver.url(path), eventTypes })
      .expect(201);
    receiver.secret = created.body.secret as string;
    return { id: created.body.id as string, secret: created.body.secret as string };
  }

  function futureNow(offsetMs = 1_000): Date {
    return new Date(Date.now() + offsetMs);
  }

  it('entrega o envelope assinado, registra tentativa e expõe histórico', async () => {
    const webhook = await createWebhook();
    const event = paymentCompletedEvent();
    const ingested = await intake.ingest(
      JSON.stringify(event),
      EVENT_TOPICS['payment.completed'],
    );
    expect(ingested).toMatchObject({ created: 1, duplicates: 0 });

    const t0 = futureNow();
    await worker.processDue({ now: t0, limit: 10 });

    expect(receiver.requests).toHaveLength(1);
    const received = receiver.requests[0];
    expect(received.signatureValid).toBe(true);
    expect(received.eventId).toBe(event.event_id);
    expect(received.payload).toEqual(event);
    expect(received.headers['x-paycore-event-type']).toBe('payment.completed');
    expect(received.headers['x-paycore-webhook-id']).toBe(webhook.id);
    expect(received.attempt).toBe('1');
    expect(Number(received.timestamp)).toBe(Math.floor(t0.getTime() / 1000));

    const list = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(list.body.total).toBe(1);
    expect(list.body.items[0]).toMatchObject({
      webhookId: webhook.id,
      eventId: event.event_id,
      eventType: 'payment.completed',
      status: 'delivered',
      origin: 'event',
      attemptCount: 1,
      maxAttempts: 2,
      lastStatusCode: 200,
    });

    const detail = await request(app.getHttpServer())
      .get(`/v1/webhook-deliveries/${list.body.items[0].id}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(detail.body.payload).toEqual(event);
    expect(detail.body.attempts).toHaveLength(1);
    expect(detail.body.attempts[0]).toMatchObject({
      attemptNumber: 1,
      outcome: 'succeeded',
      responseStatus: 200,
      signature: received.signature,
      requestTimestamp: Math.floor(t0.getTime() / 1000),
    });
    expect(detail.body.attempts[0].signature).toBe(received.signature);
  });

  it('repete a entrega após HTTP 500 com o mesmo corpo assinado', async () => {
    await createWebhook();
    const event = paymentCompletedEvent();
    await intake.ingest(JSON.stringify(event), EVENT_TOPICS['payment.completed']);
    receiver.enqueue(500, 'ok');

    const t0 = futureNow();
    await worker.processDue({ now: t0 });
    expect(receiver.requests).toHaveLength(1);

    const afterFailure = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(afterFailure.body.items[0]).toMatchObject({
      status: 'pending',
      attemptCount: 1,
      lastStatusCode: 500,
      lastError: 'http_500',
    });
    expect(new Date(afterFailure.body.items[0].nextAttemptAt).getTime()).toBeGreaterThan(
      t0.getTime(),
    );

    await worker.processDue({ now: new Date(t0.getTime() + 10) });
    expect(receiver.requests).toHaveLength(2);
    expect(receiver.requests[0].rawBody).toBe(receiver.requests[1].rawBody);
    expect(receiver.requests[1].attempt).toBe('2');
    expect(receiver.requests[1].signatureValid).toBe(true);
    expect(receiver.requests[1].timestamp).toBe(
      String(Math.floor((t0.getTime() + 10) / 1000)),
    );

    const detail = await request(app.getHttpServer())
      .get(`/v1/webhook-deliveries/${afterFailure.body.items[0].id}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(detail.body.status).toBe('delivered');
    expect(detail.body.attemptCount).toBe(2);
    expect(detail.body.attempts.map((attempt: { outcome: string }) => attempt.outcome)).toEqual([
      'http_error',
      'succeeded',
    ]);
  });

  it('trata timeout como falha transitória e entrega na tentativa seguinte', async () => {
    await createWebhook();
    const event = paymentCompletedEvent();
    await intake.ingest(JSON.stringify(event), EVENT_TOPICS['payment.completed']);
    receiver.enqueue('timeout', 'ok');

    const t0 = futureNow();
    await worker.processDue({ now: t0 });
    const afterTimeout = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(afterTimeout.body.items[0]).toMatchObject({
      status: 'pending',
      attemptCount: 1,
      lastError: 'timeout',
    });

    await worker.processDue({ now: new Date(t0.getTime() + 10) });
    const delivered = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(delivered.body.items[0]).toMatchObject({
      status: 'delivered',
      attemptCount: 2,
    });
    const detail = await request(app.getHttpServer())
      .get(`/v1/webhook-deliveries/${delivered.body.items[0].id}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(detail.body.attempts[0].outcome).toBe('timeout');
    expect(detail.body.attempts[1].outcome).toBe('succeeded');
  });

  it('move para a DLQ, lista o dead-letter e reenvia manualmente com auditoria', async () => {
    await createWebhook();
    const event = paymentCompletedEvent();
    await intake.ingest(JSON.stringify(event), EVENT_TOPICS['payment.completed']);
    receiver.enqueue(500, 500);

    const t0 = futureNow();
    await worker.processDue({ now: t0 });
    await worker.processDue({ now: new Date(t0.getTime() + 10) });

    const dead = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries?status=dead')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(dead.body.total).toBe(1);
    const original = dead.body.items[0];
    expect(original).toMatchObject({
      status: 'dead',
      attemptCount: 2,
      maxAttempts: 2,
      lastError: 'http_500',
    });

    const resent = await request(app.getHttpServer())
      .post(`/v1/webhook-deliveries/${original.id}/resend`)
      .set('X-Api-Key', ALPHA.apiKey)
      .send({ reason: 'Endpoint corrigido pelo parceiro.' })
      .expect(202);
    expect(resent.body).toMatchObject({
      webhookId: original.webhookId,
      eventId: original.eventId,
      status: 'pending',
      origin: 'manual_resend',
      sourceDeliveryId: original.id,
      resendReason: 'Endpoint corrigido pelo parceiro.',
      attemptCount: 0,
    });

    const duplicateResend = await request(app.getHttpServer())
      .post(`/v1/webhook-deliveries/${original.id}/resend`)
      .set('X-Api-Key', ALPHA.apiKey)
      .send({})
      .expect(409);
    expect(duplicateResend.body.code).toBe('webhook_resend_pending');

    receiver.enqueue('ok');
    await worker.processDue({ now: new Date(t0.getTime() + 20) });
    const newDetail = await request(app.getHttpServer())
      .get(`/v1/webhook-deliveries/${resent.body.id}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(newDetail.body).toMatchObject({
      status: 'delivered',
      origin: 'manual_resend',
      sourceDeliveryId: original.id,
      attemptCount: 1,
    });
    expect(newDetail.body.attempts).toHaveLength(1);

    const originalDetail = await request(app.getHttpServer())
      .get(`/v1/webhook-deliveries/${original.id}`)
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(originalDetail.body.status).toBe('dead');
    expect(originalDetail.body.attempts).toHaveLength(2);

    const repeated = await intake.ingest(
      JSON.stringify(event),
      EVENT_TOPICS['payment.completed'],
    );
    expect(repeated).toMatchObject({ created: 0, duplicates: 1 });
    const history = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(history.body.total).toBe(2);
  });

  it('não duplica entregas quando o mesmo evento é consumido duas vezes', async () => {
    await createWebhook();
    const event = paymentCompletedEvent();
    const first = await intake.ingest(JSON.stringify(event), EVENT_TOPICS['payment.completed']);
    const second = await intake.ingest(JSON.stringify(event), EVENT_TOPICS['payment.completed']);
    expect(first.created).toBe(1);
    expect(second).toMatchObject({ created: 0, duplicates: 1 });

    await worker.processDue({ now: futureNow() });
    expect(receiver.requests).toHaveLength(1);

    const history = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    expect(history.body.total).toBe(1);
  });

  it('entrega apenas eventos assinados pela inscrição correspondente', async () => {
    await createWebhook(['payment.rejected']);
    const completed = paymentCompletedEvent();
    const rejected = paymentRejectedEvent();
    await intake.ingest(JSON.stringify(completed), EVENT_TOPICS['payment.completed']);
    await intake.ingest(JSON.stringify(rejected), EVENT_TOPICS['payment.rejected']);
    await worker.processDue({ now: futureNow() });

    expect(receiver.requests).toHaveLength(1);
    expect(receiver.requests[0].payload).toEqual(rejected);
    expect(receiver.requests[0].headers['x-paycore-event-type']).toBe('payment.rejected');
  });

  it('isola histórico, detalhe e reenvio entre parceiros', async () => {
    await createWebhook();
    const event = paymentCompletedEvent();
    await intake.ingest(JSON.stringify(event), EVENT_TOPICS['payment.completed']);
    await worker.processDue({ now: futureNow() });
    const alphaList = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    const deliveryId = alphaList.body.items[0].id as string;

    const betaList = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', BETA.apiKey)
      .expect(200);
    expect(betaList.body).toMatchObject({ items: [], total: 0 });

    await request(app.getHttpServer())
      .get(`/v1/webhook-deliveries/${deliveryId}`)
      .set('X-Api-Key', BETA.apiKey)
      .expect(404)
      .expect((response) => expect(response.body.code).toBe('webhook_delivery_not_found'));

    await request(app.getHttpServer())
      .post(`/v1/webhook-deliveries/${deliveryId}/resend`)
      .set('X-Api-Key', BETA.apiKey)
      .send({})
      .expect(404);
  });

  it('recusa reenvio de entrega que não está na DLQ', async () => {
    await createWebhook();
    await intake.ingest(
      JSON.stringify(paymentCompletedEvent()),
      EVENT_TOPICS['payment.completed'],
    );
    await worker.processDue({ now: futureNow() });
    const list = await request(app.getHttpServer())
      .get('/v1/webhook-deliveries')
      .set('X-Api-Key', ALPHA.apiKey)
      .expect(200);
    const response = await request(app.getHttpServer())
      .post(`/v1/webhook-deliveries/${list.body.items[0].id}/resend`)
      .set('X-Api-Key', ALPHA.apiKey)
      .send({})
      .expect(409);
    expect(response.body.code).toBe('webhook_delivery_not_resendable');
  });

  it('rejeita eventos fora do contrato ou de tópico divergente', async () => {
    const event = paymentCompletedEvent();
    await expect(
      intake.ingest(JSON.stringify({ ...event, extra: true }), EVENT_TOPICS['payment.completed']),
    ).rejects.toBeInstanceOf(InvalidWebhookEventError);
    await expect(
      intake.ingest(JSON.stringify(event), EVENT_TOPICS['payment.rejected']),
    ).rejects.toBeInstanceOf(InvalidWebhookEventError);
    await expect(intake.ingest('{', EVENT_TOPICS['payment.completed'])).rejects.toBeInstanceOf(
      InvalidWebhookEventError,
    );
    const invalidAmount = paymentCompletedEvent({ amount: 0 });
    await expect(
      intake.ingest(JSON.stringify(invalidAmount), EVENT_TOPICS['payment.completed']),
    ).rejects.toBeInstanceOf(InvalidWebhookEventError);
  });

  it('bloqueia faixas privadas, loopback, link-local e metadados no destino', () => {
    for (const address of [
      '127.0.0.1',
      '10.0.0.1',
      '172.16.0.1',
      '192.168.1.1',
      '169.254.169.254',
      '100.64.0.1',
      '0.0.0.0',
      '::1',
      'fd00::1',
      'fe80::1',
      '::ffff:127.0.0.1',
    ]) {
      expect(isBlockedAddress(address)).toBe(true);
    }
    expect(isBlockedAddress('8.8.8.8')).toBe(false);
    expect(isBlockedAddress('2606:4700:4700::1111')).toBe(false);
    expect(isBlockedAddress('nao-e-ip')).toBe(true);
  });
});
