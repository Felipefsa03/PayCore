import { randomUUID } from 'node:crypto';
import { Injectable } from '@nestjs/common';
import { DatabaseService } from '../../database/database.module';
import { SupportedEventType } from '../event-types';

export const DELIVERY_STATUSES = ['pending', 'delivering', 'delivered', 'dead'] as const;
export const DELIVERY_ORIGINS = ['event', 'manual_resend'] as const;
export const ATTEMPT_OUTCOMES = [
  'succeeded',
  'http_error',
  'timeout',
  'network_error',
  'blocked_destination',
] as const;

export type DeliveryStatus = (typeof DELIVERY_STATUSES)[number];
export type DeliveryOrigin = (typeof DELIVERY_ORIGINS)[number];
export type AttemptOutcome = (typeof ATTEMPT_OUTCOMES)[number];

export interface WebhookDeliveryRecord {
  id: string;
  subscriptionId: string;
  partnerId: string;
  eventId: string;
  eventType: SupportedEventType;
  body: string;
  status: DeliveryStatus;
  origin: DeliveryOrigin;
  sourceDeliveryId: string | null;
  resendReason: string | null;
  attempts: number;
  maxAttempts: number;
  nextAttemptAt: Date;
  lastAttemptAt: Date | null;
  lastStatusCode: number | null;
  lastError: string | null;
  deliveredAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface ClaimedWebhookDelivery extends WebhookDeliveryRecord {
  url: string;
  secret: string;
}

export interface WebhookDeliveryAttemptRecord {
  id: string;
  deliveryId: string;
  attemptNumber: number;
  outcome: AttemptOutcome;
  responseStatus: number | null;
  error: string | null;
  durationMs: number | null;
  requestTimestamp: number;
  signature: string;
  createdAt: Date;
}

export interface DeliveryFilters {
  status?: DeliveryStatus;
  webhookId?: string;
  eventType?: SupportedEventType;
  limit: number;
  offset: number;
}

interface DeliveryRow extends Record<string, unknown> {
  id: string;
  subscription_id: string;
  partner_id: string;
  event_id: string;
  event_type: string;
  body: string;
  status: string;
  origin: string;
  source_delivery_id: string | null;
  resend_reason: string | null;
  attempts: number;
  max_attempts: number;
  next_attempt_at: Date;
  last_attempt_at: Date | null;
  last_status_code: number | null;
  last_error: string | null;
  delivered_at: Date | null;
  created_at: Date;
  updated_at: Date;
}

interface ClaimedDeliveryRow extends DeliveryRow {
  url: string;
  secret: string;
}

interface AttemptRow extends Record<string, unknown> {
  id: string;
  delivery_id: string;
  attempt_number: number;
  outcome: string;
  response_status: number | null;
  error: string | null;
  duration_ms: number | null;
  request_timestamp: string | number;
  signature: string;
  created_at: Date;
}

export interface RecordOutcomeInput {
  delivery: ClaimedWebhookDelivery;
  attemptNumber: number;
  outcome: AttemptOutcome;
  responseStatus: number | null;
  error: string | null;
  durationMs: number | null;
  requestTimestamp: number;
  signature: string;
  status: DeliveryStatus;
  nextAttemptAt: Date;
  now: Date;
}

@Injectable()
export class WebhookDeliveriesRepository {
  private readonly deliveries: string;
  private readonly attempts: string;

  constructor(private readonly database: DatabaseService) {
    this.deliveries = database.table('webhook_deliveries');
    this.attempts = database.table('webhook_delivery_attempts');
  }

  async insertEventDelivery(input: {
    id: string;
    subscriptionId: string;
    partnerId: string;
    eventId: string;
    eventType: SupportedEventType;
    body: string;
    maxAttempts: number;
    nextAttemptAt: Date;
  }): Promise<WebhookDeliveryRecord | null> {
    const rows = await this.database.query<DeliveryRow>(
      `INSERT INTO ${this.deliveries}
        (id, subscription_id, partner_id, event_id, event_type, body, status, origin,
         attempts, max_attempts, next_attempt_at)
       VALUES ($1, $2, $3, $4, $5, $6, 'pending', 'event', 0, $7, $8)
       ON CONFLICT DO NOTHING
       RETURNING *`,
      [
        input.id,
        input.subscriptionId,
        input.partnerId,
        input.eventId,
        input.eventType,
        input.body,
        input.maxAttempts,
        input.nextAttemptAt,
      ],
    );
    return rows[0] ? mapDelivery(rows[0]) : null;
  }

  async insertManualResend(input: {
    id: string;
    source: WebhookDeliveryRecord;
    resendReason: string | null;
    maxAttempts: number;
    nextAttemptAt: Date;
  }): Promise<WebhookDeliveryRecord> {
    const rows = await this.database.query<DeliveryRow>(
      `INSERT INTO ${this.deliveries}
        (id, subscription_id, partner_id, event_id, event_type, body, status, origin,
         source_delivery_id, resend_reason, attempts, max_attempts, next_attempt_at)
       VALUES ($1, $2, $3, $4, $5, $6, 'pending', 'manual_resend', $7, $8, 0, $9, $10)
       RETURNING *`,
      [
        input.id,
        input.source.subscriptionId,
        input.source.partnerId,
        input.source.eventId,
        input.source.eventType,
        input.source.body,
        input.source.id,
        input.resendReason,
        input.maxAttempts,
        input.nextAttemptAt,
      ],
    );
    return mapDelivery(rows[0]);
  }

  async findByIdForPartner(
    partnerId: string,
    deliveryId: string,
  ): Promise<WebhookDeliveryRecord | null> {
    const rows = await this.database.query<DeliveryRow>(
      `SELECT * FROM ${this.deliveries}
       WHERE partner_id = $1 AND id = $2`,
      [partnerId, deliveryId],
    );
    return rows[0] ? mapDelivery(rows[0]) : null;
  }

  async findPendingResendForSource(
    partnerId: string,
    sourceDeliveryId: string,
  ): Promise<WebhookDeliveryRecord | null> {
    const rows = await this.database.query<DeliveryRow>(
      `SELECT * FROM ${this.deliveries}
       WHERE partner_id = $1 AND source_delivery_id = $2
         AND status IN ('pending', 'delivering')
       ORDER BY created_at DESC
       LIMIT 1`,
      [partnerId, sourceDeliveryId],
    );
    return rows[0] ? mapDelivery(rows[0]) : null;
  }

  async listForPartner(
    partnerId: string,
    filters: DeliveryFilters,
  ): Promise<{ deliveries: WebhookDeliveryRecord[]; total: number }> {
    const conditions = ['partner_id = $1'];
    const params: unknown[] = [partnerId];
    if (filters.status) {
      params.push(filters.status);
      conditions.push(`status = $${params.length}`);
    }
    if (filters.webhookId) {
      params.push(filters.webhookId);
      conditions.push(`subscription_id = $${params.length}`);
    }
    if (filters.eventType) {
      params.push(filters.eventType);
      conditions.push(`event_type = $${params.length}`);
    }
    const where = conditions.join(' AND ');
    const limitIndex = params.length + 1;
    const offsetIndex = params.length + 2;
    const rows = await this.database.query<DeliveryRow>(
      `SELECT * FROM ${this.deliveries}
       WHERE ${where}
       ORDER BY created_at DESC, id ASC
       LIMIT $${limitIndex} OFFSET $${offsetIndex}`,
      [...params, filters.limit, filters.offset],
    );
    const totalRows = await this.database.query<{ total: number }>(
      `SELECT count(*)::int AS total FROM ${this.deliveries} WHERE ${where}`,
      params,
    );
    return {
      deliveries: rows.map(mapDelivery),
      total: totalRows[0]?.total ?? 0,
    };
  }

  async listAttempts(deliveryId: string): Promise<WebhookDeliveryAttemptRecord[]> {
    const rows = await this.database.query<AttemptRow>(
      `SELECT * FROM ${this.attempts}
       WHERE delivery_id = $1
       ORDER BY attempt_number ASC`,
      [deliveryId],
    );
    return rows.map(mapAttempt);
  }

  async claimDue(now: Date, limit: number): Promise<ClaimedWebhookDelivery[]> {
    const rows = await this.database.query<ClaimedDeliveryRow>(
      `UPDATE ${this.deliveries} AS delivery
       SET status = 'delivering', updated_at = now()
       FROM ${this.database.table('webhook_subscriptions')} AS subscription
       WHERE subscription.id = delivery.subscription_id
         AND delivery.id IN (
           SELECT id FROM ${this.deliveries}
           WHERE status = 'pending' AND next_attempt_at <= $1
           ORDER BY next_attempt_at ASC, created_at ASC
           LIMIT $2
           FOR UPDATE SKIP LOCKED
         )
       RETURNING delivery.*, subscription.url AS url, subscription.secret AS secret`,
      [now, limit],
    );
    return rows.map((row) => ({ ...mapDelivery(row), url: row.url, secret: row.secret }));
  }

  async recoverStale(now: Date, lockTtlMs: number): Promise<number> {
    const cutoff = new Date(now.getTime() - lockTtlMs);
    return this.database.execute(
      `UPDATE ${this.deliveries}
       SET status = 'pending',
           next_attempt_at = LEAST(next_attempt_at, $1),
           last_error = COALESCE(last_error, 'worker_interrupted'),
           updated_at = now()
       WHERE status = 'delivering' AND updated_at <= $2`,
      [now, cutoff],
    );
  }

  async recordOutcome(input: RecordOutcomeInput): Promise<WebhookDeliveryRecord> {
    return this.database.transaction(async (client) => {
      await client.query(
        `INSERT INTO ${this.attempts}
          (id, delivery_id, attempt_number, outcome, response_status, error, duration_ms,
           request_timestamp, signature)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
        [
          randomUUID(),
          input.delivery.id,
          input.attemptNumber,
          input.outcome,
          input.responseStatus,
          input.error,
          input.durationMs,
          input.requestTimestamp,
          input.signature,
        ],
      );
      const result = await client.query<DeliveryRow>(
        `UPDATE ${this.deliveries}
         SET status = $2,
             attempts = $3,
             next_attempt_at = $4,
             last_attempt_at = $5,
             last_status_code = $6,
             last_error = $7,
             delivered_at = $8,
             updated_at = now()
         WHERE id = $1
         RETURNING *`,
        [
          input.delivery.id,
          input.status,
          input.attemptNumber,
          input.nextAttemptAt,
          input.now,
          input.responseStatus,
          input.error,
          input.status === 'delivered' ? input.now : null,
        ],
      );
      return mapDelivery(result.rows[0]);
    });
  }
}

function mapDelivery(row: DeliveryRow): WebhookDeliveryRecord {
  return {
    id: row.id,
    subscriptionId: row.subscription_id,
    partnerId: row.partner_id,
    eventId: row.event_id,
    eventType: row.event_type as SupportedEventType,
    body: row.body,
    status: row.status as DeliveryStatus,
    origin: row.origin as DeliveryOrigin,
    sourceDeliveryId: row.source_delivery_id,
    resendReason: row.resend_reason,
    attempts: row.attempts,
    maxAttempts: row.max_attempts,
    nextAttemptAt: row.next_attempt_at,
    lastAttemptAt: row.last_attempt_at,
    lastStatusCode: row.last_status_code,
    lastError: row.last_error,
    deliveredAt: row.delivered_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function mapAttempt(row: AttemptRow): WebhookDeliveryAttemptRecord {
  return {
    id: row.id,
    deliveryId: row.delivery_id,
    attemptNumber: row.attempt_number,
    outcome: row.outcome as AttemptOutcome,
    responseStatus: row.response_status,
    error: row.error,
    durationMs: row.duration_ms,
    requestTimestamp: Number(row.request_timestamp),
    signature: row.signature,
    createdAt: row.created_at,
  };
}
