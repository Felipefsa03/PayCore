import { Module } from '@nestjs/common';
import { WebhookDeliveriesController } from './delivery/webhook-deliveries.controller';
import { WebhookDeliveriesRepository } from './delivery/webhook-deliveries.repository';
import { WebhookDeliveriesService } from './delivery/webhook-deliveries.service';
import { WebhookDeliveryWorker } from './delivery/webhook-delivery.worker';
import { WebhookDestinationGuard } from './delivery/webhook-destination.guard';
import { WebhookEventIntakeService, WebhookEventParser } from './delivery/webhook-event.intake';
import { WebhookKafkaConsumer } from './delivery/webhook-kafka.consumer';
import { WebhookSigner } from './delivery/webhook-signer';
import { WebhooksController } from './webhooks.controller';
import { WebhooksRepository } from './webhooks.repository';
import { WebhooksService } from './webhooks.service';

@Module({
  controllers: [WebhooksController, WebhookDeliveriesController],
  providers: [
    WebhooksRepository,
    WebhooksService,
    WebhookDeliveriesRepository,
    WebhookDeliveriesService,
    WebhookEventParser,
    WebhookEventIntakeService,
    WebhookSigner,
    WebhookDestinationGuard,
    WebhookDeliveryWorker,
    WebhookKafkaConsumer,
  ],
})
export class WebhooksModule {}
