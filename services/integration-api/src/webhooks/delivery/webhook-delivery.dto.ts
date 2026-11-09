import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { Type } from 'class-transformer';
import {
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
} from 'class-validator';
import { SUPPORTED_EVENT_TYPES, SupportedEventType } from '../event-types';
import {
  ATTEMPT_OUTCOMES,
  AttemptOutcome,
  DELIVERY_ORIGINS,
  DELIVERY_STATUSES,
  DeliveryOrigin,
  DeliveryStatus,
  WebhookDeliveryAttemptRecord,
  WebhookDeliveryRecord,
} from './webhook-deliveries.repository';

export class ListWebhookDeliveriesQuery {
  @ApiPropertyOptional({ enum: [...DELIVERY_STATUSES], description: 'Filtra pelo estado da entrega.' })
  @IsOptional()
  @IsIn([...DELIVERY_STATUSES])
  status?: DeliveryStatus;

  @ApiPropertyOptional({ format: 'uuid', description: 'Filtra por uma inscrição de webhook.' })
  @IsOptional()
  @IsUUID()
  webhookId?: string;

  @ApiPropertyOptional({ enum: [...SUPPORTED_EVENT_TYPES], description: 'Filtra pelo tipo de evento.' })
  @IsOptional()
  @IsIn([...SUPPORTED_EVENT_TYPES])
  eventType?: SupportedEventType;

  @ApiPropertyOptional({ type: Number, default: 20, minimum: 1, maximum: 100 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  limit = 20;

  @ApiPropertyOptional({ type: Number, default: 0, minimum: 0 })
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  offset = 0;
}

export class ResendWebhookDeliveryRequest {
  @ApiPropertyOptional({
    maxLength: 500,
    example: 'Parceiro corrigiu o endpoint após o incidente.',
    description: 'Justificativa auditável do reenvio manual.',
  })
  @IsOptional()
  @IsString()
  @MaxLength(500)
  reason?: string;
}

export class WebhookDeliveryAttemptResponse {
  @ApiProperty()
  attemptNumber!: number;

  @ApiProperty({ enum: [...ATTEMPT_OUTCOMES] })
  outcome!: AttemptOutcome;

  @ApiProperty({ type: Number, nullable: true, example: 500 })
  responseStatus!: number | null;

  @ApiProperty({ type: String, nullable: true, maxLength: 1000 })
  error!: string | null;

  @ApiProperty({ type: Number, nullable: true })
  durationMs!: number | null;

  @ApiProperty({
    description: 'Unix timestamp (segundos) usado no cabeçalho e na assinatura.',
  })
  requestTimestamp!: number;

  @ApiProperty({ example: 'v1=...' })
  signature!: string;

  @ApiProperty({ format: 'date-time' })
  createdAt!: string;
}

export class WebhookDeliveryResponse {
  @ApiProperty({ format: 'uuid' })
  id!: string;

  @ApiProperty({ format: 'uuid', description: 'Inscrição de webhook que originou a entrega.' })
  webhookId!: string;

  @ApiProperty({ format: 'uuid' })
  eventId!: string;

  @ApiProperty({ enum: [...SUPPORTED_EVENT_TYPES] })
  eventType!: SupportedEventType;

  @ApiProperty({ enum: [...DELIVERY_STATUSES] })
  status!: DeliveryStatus;

  @ApiProperty({ enum: [...DELIVERY_ORIGINS] })
  origin!: DeliveryOrigin;

  @ApiProperty({ format: 'uuid', type: String, nullable: true })
  sourceDeliveryId!: string | null;

  @ApiProperty({ type: String, nullable: true, maxLength: 500 })
  resendReason!: string | null;

  @ApiProperty()
  attemptCount!: number;

  @ApiProperty()
  maxAttempts!: number;

  @ApiProperty({ format: 'date-time', type: String, nullable: true })
  nextAttemptAt!: string | null;

  @ApiProperty({ format: 'date-time', type: String, nullable: true })
  lastAttemptAt!: string | null;

  @ApiProperty({ type: Number, nullable: true, example: 200 })
  lastStatusCode!: number | null;

  @ApiProperty({ type: String, nullable: true, maxLength: 1000 })
  lastError!: string | null;

  @ApiProperty({ format: 'date-time', type: String, nullable: true })
  deliveredAt!: string | null;

  @ApiProperty({ format: 'date-time' })
  createdAt!: string;

  @ApiProperty({ format: 'date-time' })
  updatedAt!: string;
}

export class WebhookDeliveryDetailResponse extends WebhookDeliveryResponse {
  @ApiProperty({
    type: 'object',
    additionalProperties: true,
    description: 'Envelope do evento exatamente como assinado e enviado ao parceiro.',
  })
  payload!: Record<string, unknown>;

  @ApiProperty({ type: [WebhookDeliveryAttemptResponse] })
  attempts!: WebhookDeliveryAttemptResponse[];
}

export class WebhookDeliveryListResponse {
  @ApiProperty({ type: [WebhookDeliveryResponse] })
  items!: WebhookDeliveryResponse[];

  @ApiProperty()
  total!: number;

  @ApiProperty()
  limit!: number;

  @ApiProperty()
  offset!: number;
}

export function toWebhookDeliveryResponse(record: WebhookDeliveryRecord): WebhookDeliveryResponse {
  return {
    id: record.id,
    webhookId: record.subscriptionId,
    eventId: record.eventId,
    eventType: record.eventType,
    status: record.status,
    origin: record.origin,
    sourceDeliveryId: record.sourceDeliveryId,
    resendReason: record.resendReason,
    attemptCount: record.attempts,
    maxAttempts: record.maxAttempts,
    nextAttemptAt: terminal(record.status) ? null : record.nextAttemptAt.toISOString(),
    lastAttemptAt: record.lastAttemptAt?.toISOString() ?? null,
    lastStatusCode: record.lastStatusCode,
    lastError: record.lastError,
    deliveredAt: record.deliveredAt?.toISOString() ?? null,
    createdAt: record.createdAt.toISOString(),
    updatedAt: record.updatedAt.toISOString(),
  };
}

export function toWebhookDeliveryDetailResponse(
  record: WebhookDeliveryRecord,
  attempts: WebhookDeliveryAttemptRecord[],
): WebhookDeliveryDetailResponse {
  return {
    ...toWebhookDeliveryResponse(record),
    payload: JSON.parse(record.body) as Record<string, unknown>,
    attempts: attempts.map((attempt) => ({
      attemptNumber: attempt.attemptNumber,
      outcome: attempt.outcome,
      responseStatus: attempt.responseStatus,
      error: attempt.error,
      durationMs: attempt.durationMs,
      requestTimestamp: attempt.requestTimestamp,
      signature: attempt.signature,
      createdAt: attempt.createdAt.toISOString(),
    })),
  };
}

function terminal(status: DeliveryStatus): boolean {
  return status === 'delivered' || status === 'dead';
}
