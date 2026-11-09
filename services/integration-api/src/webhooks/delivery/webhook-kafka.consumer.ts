import { Inject, Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { Consumer, Kafka } from 'kafkajs';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import { EVENT_TOPICS, WebhookEventIntakeService } from './webhook-event.intake';

/**
 * Consome os tópicos versionados de pagamento e materializa uma entrega por
 * inscrição ativa. Kafka é at-least-once; a unicidade
 * `(subscription_id, event_id)` evita criar entregas duplicadas quando o mesmo
 * evento é reentregue.
 *
 * O consumidor só conecta com `PAYCORE_WEBHOOK_KAFKA_ENABLED=1`; os testes de
 * integração exercitam a intake diretamente sem broker.
 */
@Injectable()
export class WebhookKafkaConsumer implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(WebhookKafkaConsumer.name);
  private consumer: Consumer | null = null;
  private runPromise: Promise<void> | null = null;

  constructor(
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    private readonly intake: WebhookEventIntakeService,
  ) {}

  async onModuleInit(): Promise<void> {
    if (!this.config.webhooks.kafka.enabled) {
      this.logger.log(
        JSON.stringify({ event: 'webhook_kafka_disabled', reason: 'PAYCORE_WEBHOOK_KAFKA_ENABLED!=1' }),
      );
      return;
    }
    const kafka = new Kafka({
      clientId: this.config.webhooks.kafka.clientId,
      brokers: [...this.config.webhooks.kafka.brokers],
    });
    this.consumer = kafka.consumer({
      groupId: this.config.webhooks.kafka.groupId,
      sessionTimeout: 30_000,
      heartbeatInterval: 3_000,
    });
    await this.consumer.connect();
    await this.consumer.subscribe({
      topics: Object.values(EVENT_TOPICS),
      fromBeginning: false,
    });
    this.runPromise = this.consumer.run({
      eachMessage: async ({ topic, message }) => {
        if (!message.value) {
          throw new Error(`evento Kafka sem corpo no tópico ${topic}`);
        }
        const result = await this.intake.ingest(message.value.toString('utf8'), topic);
        this.logger.debug(
          JSON.stringify({
            event: 'webhook_event_ingested',
            topic,
            eventId: result.eventId,
            eventType: result.eventType,
            subscriptions: result.subscriptions,
            created: result.created,
            duplicates: result.duplicates,
          }),
        );
      },
    });
    this.runPromise.catch((error: Error) => {
      this.logger.error(
        JSON.stringify({ event: 'webhook_kafka_run_failed', error: error.message }),
      );
    });
    this.logger.log(
      JSON.stringify({
        event: 'webhook_kafka_started',
        groupId: this.config.webhooks.kafka.groupId,
        topics: Object.values(EVENT_TOPICS),
      }),
    );
  }

  async onModuleDestroy(): Promise<void> {
    const consumer = this.consumer;
    this.consumer = null;
    if (!consumer) {
      return;
    }
    try {
      await consumer.stop();
    } catch (error) {
      this.logger.warn(
        JSON.stringify({ event: 'webhook_kafka_stop_failed', error: (error as Error).message }),
      );
    }
    try {
      await consumer.disconnect();
    } catch (error) {
      this.logger.warn(
        JSON.stringify({
          event: 'webhook_kafka_disconnect_failed',
          error: (error as Error).message,
        }),
      );
    }
    await this.runPromise?.catch(() => undefined);
  }
}
