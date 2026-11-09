import {
  Inject,
  Injectable,
  Logger,
  OnModuleDestroy,
  OnModuleInit,
} from '@nestjs/common';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import {
  ClaimedWebhookDelivery,
  AttemptOutcome,
  DeliveryStatus,
  WebhookDeliveriesRepository,
  WebhookDeliveryRecord,
} from './webhook-deliveries.repository';
import { BlockedDestinationError, WebhookDestinationGuard } from './webhook-destination.guard';
import { WebhookSigner } from './webhook-signer';

export interface ProcessDueOptions {
  readonly now?: Date;
  readonly limit?: number;
}

/**
 * Entrega as notificações pendentes com assinatura HMAC, timeout e retry
 * exponencial. O claim usa `FOR UPDATE SKIP LOCKED`, então várias instâncias
 * podem competir sem enviar a mesma linha ao mesmo tempo.
 */
@Injectable()
export class WebhookDeliveryWorker implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(WebhookDeliveryWorker.name);
  private timer: NodeJS.Timeout | null = null;
  private running = false;

  constructor(
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    private readonly deliveries: WebhookDeliveriesRepository,
    private readonly signer: WebhookSigner,
    private readonly destinations: WebhookDestinationGuard,
  ) {}

  onModuleInit(): void {
    this.start();
  }

  onModuleDestroy(): void {
    this.stop();
  }

  start(): void {
    if (!this.config.webhooks.workerEnabled || this.timer) {
      return;
    }
    void this.recoverStale();
    this.timer = setInterval(() => {
      void this.tick();
    }, this.config.webhooks.pollIntervalMs);
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  async recoverStale(): Promise<number> {
    try {
      const recovered = await this.deliveries.recoverStale(
        new Date(),
        this.config.webhooks.lockTtlMs,
      );
      if (recovered > 0) {
        this.logger.warn(
          JSON.stringify({ event: 'webhook_deliveries_recovered', count: recovered }),
        );
      }
      return recovered;
    } catch (error) {
      this.logger.error(
        JSON.stringify({
          event: 'webhook_delivery_recovery_failed',
          error: (error as Error).message,
        }),
      );
      return 0;
    }
  }

  async tick(): Promise<void> {
    if (this.running) {
      return;
    }
    this.running = true;
    try {
      await this.recoverStale();
      await this.processDue();
    } catch (error) {
      this.logger.error(
        JSON.stringify({
          event: 'webhook_worker_tick_failed',
          error: (error as Error).message,
        }),
      );
    } finally {
      this.running = false;
    }
  }

  async processDue(options: ProcessDueOptions = {}): Promise<number> {
    const now = options.now ?? new Date();
    const limit = options.limit ?? this.config.webhooks.batchSize;
    const claimed = await this.deliveries.claimDue(now, limit);
    const results = await Promise.allSettled(
      claimed.map((delivery) => this.deliver(delivery, now)),
    );
    return results.filter((result) => result.status === 'fulfilled').length;
  }

  private async deliver(delivery: ClaimedWebhookDelivery, now: Date): Promise<void> {
    const attemptNumber = delivery.attempts + 1;
    const requestTimestamp = Math.floor(now.getTime() / 1000);
    const signature = this.signer.signature(delivery.secret, requestTimestamp, delivery.body);
    const headers = {
      'content-type': 'application/json',
      'user-agent': 'PayCore-Webhook/0.1',
      'x-paycore-webhook-id': delivery.subscriptionId,
      'x-paycore-delivery-id': delivery.id,
      'x-paycore-event-id': delivery.eventId,
      'x-paycore-event-type': delivery.eventType,
      'x-paycore-attempt': String(attemptNumber),
      'x-paycore-timestamp': String(requestTimestamp),
      'x-paycore-signature': signature,
    };
    const startedAt = Date.now();
    let outcome: AttemptOutcome = 'network_error';
    let responseStatus: number | null = null;
    let error: string | null = null;
    let retryable = true;
    try {
      await this.destinations.assertAllowed(delivery.url, this.config.webhooks.timeoutMs);
      const response = await fetch(delivery.url, {
        method: 'POST',
        headers,
        body: delivery.body,
        redirect: 'manual',
        signal: AbortSignal.timeout(this.config.webhooks.timeoutMs),
      });
      responseStatus = response.status;
      await response.body?.cancel();
      if (response.status >= 200 && response.status < 300) {
        outcome = 'succeeded';
        error = null;
      } else {
        outcome = 'http_error';
        error = `http_${response.status}`;
        retryable =
          response.status === 408 ||
          response.status === 425 ||
          response.status === 429 ||
          response.status >= 500;
      }
    } catch (caught) {
      responseStatus = null;
      const failure = caught as Error;
      if (failure instanceof BlockedDestinationError) {
        outcome = 'blocked_destination';
        error = failure.reason;
        retryable = false;
      } else if (failure.name === 'AbortError' || failure.name === 'TimeoutError') {
        outcome = 'timeout';
        error = 'timeout';
      } else {
        outcome = 'network_error';
        error = truncate(failure.message || 'network_error');
      }
    }
    const durationMs = Date.now() - startedAt;
    const status = decideStatus(outcome, attemptNumber, delivery.maxAttempts, retryable);
    const nextAttemptAt =
      status === 'pending' ? new Date(now.getTime() + this.backoffMs(attemptNumber)) : now;
    await this.deliveries.recordOutcome({
      delivery,
      attemptNumber,
      outcome,
      responseStatus,
      error,
      durationMs,
      requestTimestamp,
      signature,
      status,
      nextAttemptAt,
      now,
    });
    if (status === 'dead') {
      this.logger.warn(
        JSON.stringify({
          event: 'webhook_delivery_dead',
          deliveryId: delivery.id,
          eventId: delivery.eventId,
          outcome,
          attempt: attemptNumber,
        }),
      );
    } else if (status === 'pending') {
      this.logger.warn(
        JSON.stringify({
          event: 'webhook_delivery_retry',
          deliveryId: delivery.id,
          eventId: delivery.eventId,
          outcome,
          attempt: attemptNumber,
        }),
      );
    }
  }

  backoffMs(attemptNumber: number): number {
    const exponential = this.config.webhooks.backoffBaseMs * 2 ** (attemptNumber - 1);
    return Math.min(exponential, this.config.webhooks.backoffMaxMs);
  }
}

function decideStatus(
  outcome: AttemptOutcome,
  attemptNumber: number,
  maxAttempts: number,
  retryable: boolean,
): DeliveryStatus {
  if (outcome === 'succeeded') {
    return 'delivered';
  }
  if (!retryable || attemptNumber >= maxAttempts) {
    return 'dead';
  }
  return 'pending';
}

function truncate(value: string): string {
  return value.length > 1_000 ? `${value.slice(0, 1_000)}…` : value;
}

export type { WebhookDeliveryRecord };
