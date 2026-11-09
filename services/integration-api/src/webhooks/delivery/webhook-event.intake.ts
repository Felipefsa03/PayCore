import { randomUUID } from 'node:crypto';
import { Inject, Injectable } from '@nestjs/common';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import { SUPPORTED_EVENT_TYPES, SupportedEventType } from '../event-types';
import { WebhooksRepository } from '../webhooks.repository';
import { WebhookDeliveriesRepository } from './webhook-deliveries.repository';

export const MAX_WEBHOOK_BODY_BYTES = 256 * 1024;

export const EVENT_TOPICS: Readonly<Record<SupportedEventType, string>> = {
  'payment.completed': 'paycore.payments.payment-completed.v1',
  'payment.rejected': 'paycore.payments.payment-rejected.v1',
};

const TOP_LEVEL_KEYS = new Set([
  'event_id',
  'event_type',
  'event_version',
  'occurred_at',
  'correlation_id',
  'aggregate',
  'data',
]);
const AGGREGATE_KEYS = new Set(['type', 'id']);
const COMPLETED_DATA_KEYS = new Set([
  'transferId',
  'state',
  'sourceAccountId',
  'destinationAccountId',
  'amount',
  'currency',
  'correlationId',
  'journalEntryId',
]);
const REJECTED_DATA_KEYS = new Set([
  'transferId',
  'state',
  'sourceAccountId',
  'destinationAccountId',
  'amount',
  'currency',
  'correlationId',
  'failureReason',
]);
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const TIMESTAMP_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/;

export interface PaymentEventEnvelope {
  readonly event_id: string;
  readonly event_type: SupportedEventType;
  readonly event_version: 1;
  readonly occurred_at: string;
  readonly correlation_id: string;
  readonly aggregate: { readonly type: 'payment'; readonly id: string };
  readonly data: Record<string, unknown>;
}

export class InvalidWebhookEventError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidWebhookEventError';
  }
}

@Injectable()
export class WebhookEventParser {
  parse(rawBody: string, topic?: string): PaymentEventEnvelope {
    if (Buffer.byteLength(rawBody, 'utf8') > MAX_WEBHOOK_BODY_BYTES) {
      throw new InvalidWebhookEventError('evento excede o limite de tamanho');
    }
    let value: unknown;
    try {
      value = JSON.parse(rawBody);
    } catch {
      throw new InvalidWebhookEventError('evento não é JSON válido');
    }
    if (!isObject(value)) {
      throw new InvalidWebhookEventError('envelope precisa ser um objeto');
    }
    assertExactKeys(value, TOP_LEVEL_KEYS, 'envelope');
    const eventId = uuid(value.event_id, 'event_id');
    const eventType = supportedType(value.event_type);
    if (value.event_version !== 1) {
      throw new InvalidWebhookEventError('event_version precisa ser 1');
    }
    const occurredAt = value.occurred_at;
    if (typeof occurredAt !== 'string' || !TIMESTAMP_PATTERN.test(occurredAt)) {
      throw new InvalidWebhookEventError('occurred_at precisa ser UTC ISO-8601');
    }
    if (Number.isNaN(Date.parse(occurredAt))) {
      throw new InvalidWebhookEventError('occurred_at inválido');
    }
    const correlationId = uuid(value.correlation_id, 'correlation_id');
    if (!isObject(value.aggregate)) {
      throw new InvalidWebhookEventError('aggregate precisa ser um objeto');
    }
    assertExactKeys(value.aggregate, AGGREGATE_KEYS, 'aggregate');
    if (value.aggregate.type !== 'payment') {
      throw new InvalidWebhookEventError('aggregate.type precisa ser payment');
    }
    const aggregateId = uuid(value.aggregate.id, 'aggregate.id');
    if (!isObject(value.data)) {
      throw new InvalidWebhookEventError('data precisa ser um objeto');
    }
    const expectedTopic = EVENT_TOPICS[eventType];
    if (topic !== undefined && topic !== expectedTopic) {
      throw new InvalidWebhookEventError(`tópico ${topic} não corresponde a ${eventType}`);
    }
    const data = eventType === 'payment.completed'
      ? validateCompletedData(value.data, aggregateId)
      : validateRejectedData(value.data, aggregateId);
    return {
      event_id: eventId,
      event_type: eventType,
      event_version: 1,
      occurred_at: occurredAt,
      correlation_id: correlationId,
      aggregate: { type: 'payment', id: aggregateId },
      data,
    };
  }

  canonicalBody(event: PaymentEventEnvelope): string {
    return JSON.stringify(event);
  }
}

export interface IntakeResult {
  readonly eventId: string;
  readonly eventType: SupportedEventType;
  readonly subscriptions: number;
  readonly created: number;
  readonly duplicates: number;
}

@Injectable()
export class WebhookEventIntakeService {
  constructor(
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    private readonly parser: WebhookEventParser,
    private readonly subscriptions: WebhooksRepository,
    private readonly deliveries: WebhookDeliveriesRepository,
  ) {}

  async ingest(rawBody: string, topic?: string): Promise<IntakeResult> {
    const event = this.parser.parse(rawBody, topic);
    const body = this.parser.canonicalBody(event);
    const targets = await this.subscriptions.listActiveByEventType(event.event_type);
    const now = new Date();
    let created = 0;
    for (const subscription of targets) {
      const delivery = await this.deliveries.insertEventDelivery({
        id: randomUUID(),
        subscriptionId: subscription.id,
        partnerId: subscription.partnerId,
        eventId: event.event_id,
        eventType: event.event_type,
        body,
        maxAttempts: this.config.webhooks.maxAttempts,
        nextAttemptAt: now,
      });
      if (delivery) {
        created += 1;
      }
    }
    return {
      eventId: event.event_id,
      eventType: event.event_type,
      subscriptions: targets.length,
      created,
      duplicates: targets.length - created,
    };
  }
}

function validateCompletedData(
  data: Record<string, unknown>,
  aggregateId: string,
): Record<string, unknown> {
  assertExactKeys(data, COMPLETED_DATA_KEYS, 'data');
  assertCommonData(data, aggregateId, 'COMPLETED');
  uuid(data.journalEntryId, 'data.journalEntryId');
  return { ...data };
}

function validateRejectedData(
  data: Record<string, unknown>,
  aggregateId: string,
): Record<string, unknown> {
  assertExactKeys(data, REJECTED_DATA_KEYS, 'data');
  assertCommonData(data, aggregateId, 'REJECTED');
  if (
    typeof data.failureReason !== 'string' ||
    data.failureReason.trim().length === 0 ||
    data.failureReason.length > 2_000
  ) {
    throw new InvalidWebhookEventError('data.failureReason inválido');
  }
  return { ...data };
}

function assertCommonData(
  data: Record<string, unknown>,
  aggregateId: string,
  expectedState: string,
): void {
  const transferId = uuid(data.transferId, 'data.transferId');
  if (transferId !== aggregateId) {
    throw new InvalidWebhookEventError('data.transferId difere de aggregate.id');
  }
  if (data.state !== expectedState) {
    throw new InvalidWebhookEventError(`data.state precisa ser ${expectedState}`);
  }
  uuid(data.sourceAccountId, 'data.sourceAccountId');
  uuid(data.destinationAccountId, 'data.destinationAccountId');
  if (!Number.isSafeInteger(data.amount) || (data.amount as number) < 1) {
    throw new InvalidWebhookEventError('data.amount precisa ser inteiro positivo');
  }
  if (typeof data.currency !== 'string' || !/^[A-Z]{3}$/.test(data.currency)) {
    throw new InvalidWebhookEventError('data.currency inválida');
  }
  uuid(data.correlationId, 'data.correlationId');
}

function assertExactKeys(
  value: Record<string, unknown>,
  allowed: Set<string>,
  name: string,
): void {
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) {
      throw new InvalidWebhookEventError(`${name}.${key} não faz parte do contrato`);
    }
  }
  for (const key of allowed) {
    if (!(key in value)) {
      throw new InvalidWebhookEventError(`${name}.${key} é obrigatório`);
    }
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function uuid(value: unknown, field: string): string {
  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) {
    throw new InvalidWebhookEventError(`${field} precisa ser UUID`);
  }
  return value;
}

function supportedType(value: unknown): SupportedEventType {
  if (typeof value !== 'string' || !SUPPORTED_EVENT_TYPES.includes(value as SupportedEventType)) {
    throw new InvalidWebhookEventError('event_type não suportado');
  }
  return value as SupportedEventType;
}
