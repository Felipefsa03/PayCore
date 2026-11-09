import { randomUUID } from 'node:crypto';
import { Inject, Injectable } from '@nestjs/common';
import { ApiError } from '../../common/api-error';
import { APP_CONFIG, AppConfig, PartnerConfig } from '../../config/app-config';
import {
  WebhookDeliveriesRepository,
  WebhookDeliveryRecord,
} from './webhook-deliveries.repository';
import {
  ListWebhookDeliveriesQuery,
  ResendWebhookDeliveryRequest,
  WebhookDeliveryDetailResponse,
  WebhookDeliveryListResponse,
  WebhookDeliveryResponse,
  toWebhookDeliveryDetailResponse,
  toWebhookDeliveryResponse,
} from './webhook-delivery.dto';

@Injectable()
export class WebhookDeliveriesService {
  constructor(
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    private readonly repository: WebhookDeliveriesRepository,
  ) {}

  async list(
    partner: PartnerConfig,
    query: ListWebhookDeliveriesQuery,
  ): Promise<WebhookDeliveryListResponse> {
    const result = await this.repository.listForPartner(partner.id, {
      status: query.status,
      webhookId: query.webhookId,
      eventType: query.eventType,
      limit: query.limit,
      offset: query.offset,
    });
    return {
      items: result.deliveries.map(toWebhookDeliveryResponse),
      total: result.total,
      limit: query.limit,
      offset: query.offset,
    };
  }

  async detail(partner: PartnerConfig, deliveryId: string): Promise<WebhookDeliveryDetailResponse> {
    const delivery = await this.requireDelivery(partner, deliveryId);
    const attempts = await this.repository.listAttempts(delivery.id);
    return toWebhookDeliveryDetailResponse(delivery, attempts);
  }

  async resend(
    partner: PartnerConfig,
    deliveryId: string,
    request: ResendWebhookDeliveryRequest,
  ): Promise<WebhookDeliveryResponse> {
    const source = await this.requireDelivery(partner, deliveryId);
    if (source.status !== 'dead') {
      throw new ApiError(
        409,
        'webhook_delivery_not_resendable',
        'Somente entregas na dead-letter queue podem ser reenviadas manualmente.',
      );
    }
    const pending = await this.repository.findPendingResendForSource(partner.id, source.id);
    if (pending) {
      throw new ApiError(
        409,
        'webhook_resend_pending',
        'Já existe um reenvio manual pendente para esta entrega.',
        { deliveryId: pending.id },
      );
    }
    const now = new Date();
    try {
      const delivery = await this.repository.insertManualResend({
        id: randomUUID(),
        source,
        resendReason: request.reason?.trim() || null,
        maxAttempts: this.config.webhooks.maxAttempts,
        nextAttemptAt: now,
      });
      return toWebhookDeliveryResponse(delivery);
    } catch (error) {
      if ((error as { code?: string }).code === '23505') {
        throw new ApiError(
          409,
          'webhook_resend_pending',
          'Já existe um reenvio manual pendente para esta entrega.',
        );
      }
      throw error;
    }
  }

  private async requireDelivery(
    partner: PartnerConfig,
    deliveryId: string,
  ): Promise<WebhookDeliveryRecord> {
    const delivery = await this.repository.findByIdForPartner(partner.id, deliveryId);
    if (!delivery) {
      throw new ApiError(404, 'webhook_delivery_not_found', 'Entrega não encontrada.');
    }
    return delivery;
  }
}
