import { randomUUID } from 'node:crypto';

export function paymentCompletedEvent(overrides: {
  eventId?: string;
  correlationId?: string;
  aggregateId?: string;
  ownerId?: string;
  amount?: number;
} = {}): Record<string, unknown> {
  const eventId = overrides.eventId ?? randomUUID();
  const correlationId = overrides.correlationId ?? randomUUID();
  const aggregateId = overrides.aggregateId ?? randomUUID();
  return {
    event_id: eventId,
    event_type: 'payment.completed',
    event_version: 1,
    occurred_at: new Date().toISOString(),
    correlation_id: correlationId,
    aggregate: { type: 'payment', id: aggregateId },
    data: {
      transferId: aggregateId,
      state: 'COMPLETED',
      sourceAccountId: randomUUID(),
      destinationAccountId: randomUUID(),
      amount: overrides.amount ?? 1_500,
      currency: 'BRL',
      correlationId,
      journalEntryId: randomUUID(),
    },
  };
}

export function paymentRejectedEvent(overrides: {
  eventId?: string;
  correlationId?: string;
  aggregateId?: string;
  failureReason?: string;
} = {}): Record<string, unknown> {
  const eventId = overrides.eventId ?? randomUUID();
  const correlationId = overrides.correlationId ?? randomUUID();
  const aggregateId = overrides.aggregateId ?? randomUUID();
  return {
    event_id: eventId,
    event_type: 'payment.rejected',
    event_version: 1,
    occurred_at: new Date().toISOString(),
    correlation_id: correlationId,
    aggregate: { type: 'payment', id: aggregateId },
    data: {
      transferId: aggregateId,
      state: 'REJECTED',
      sourceAccountId: randomUUID(),
      destinationAccountId: randomUUID(),
      amount: 900,
      currency: 'BRL',
      correlationId,
      failureReason: overrides.failureReason ?? 'INSUFFICIENT_FUNDS',
    },
  };
}
