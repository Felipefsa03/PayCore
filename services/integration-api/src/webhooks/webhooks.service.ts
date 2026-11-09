import { randomBytes, randomUUID } from 'node:crypto';
import { Injectable } from '@nestjs/common';
import { ApiError } from '../common/api-error';
import { PartnerConfig } from '../config/app-config';
import { CreateWebhookRequest, WebhookCreatedResponse, WebhookResponse } from './webhook.dto';
import { WebhookRecord, WebhooksRepository } from './webhooks.repository';

@Injectable()
export class WebhooksService {
  constructor(private readonly repository: WebhooksRepository) {}

  async create(
    partner: PartnerConfig,
    request: CreateWebhookRequest,
  ): Promise<WebhookCreatedResponse> {
    const record = await this.repository.create({
      id: randomUUID(),
      partnerId: partner.id,
      ownerId: partner.ownerId,
      url: request.url,
      eventTypes: [...new Set(request.eventTypes)],
      description: request.description ?? null,
      active: true,
      secret: `whsec_${randomBytes(32).toString('base64url')}`,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    return { ...toResponse(record), secret: record.secret };
  }

  async list(partner: PartnerConfig): Promise<WebhookResponse[]> {
    const records = await this.repository.listByPartner(partner.id);
    return records.map(toResponse);
  }

  async remove(partner: PartnerConfig, id: string): Promise<void> {
    const deleted = await this.repository.deleteById(partner.id, id);
    if (!deleted) {
      throw new ApiError(404, 'webhook_not_found', 'Webhook não encontrado.');
    }
  }
}

function toResponse(record: WebhookRecord): WebhookResponse {
  return {
    id: record.id,
    url: record.url,
    eventTypes: record.eventTypes,
    description: record.description,
    active: record.active,
    createdAt: record.createdAt.toISOString(),
    updatedAt: record.updatedAt.toISOString(),
  };
}
