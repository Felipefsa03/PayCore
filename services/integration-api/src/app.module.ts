import { Module } from '@nestjs/common';
import { APP_FILTER, APP_GUARD, APP_PIPE } from '@nestjs/core';
import { ApiExceptionFilter } from './common/api-exception.filter';
import { PartnerAuthGuard } from './common/partner-auth.guard';
import { RateLimitGuard } from './common/rate-limit.guard';
import { createValidationPipe } from './common/validation';
import { ConfigModule } from './config/config.module';
import { DatabaseModule } from './database/database.module';
import { HealthController } from './health/health.controller';
import { TransactionsModule } from './transactions/transactions.module';
import { WebhooksModule } from './webhooks/webhooks.module';

@Module({
  imports: [ConfigModule, DatabaseModule, TransactionsModule, WebhooksModule],
  controllers: [HealthController],
  providers: [
    PartnerAuthGuard,
    RateLimitGuard,
    { provide: APP_GUARD, useExisting: PartnerAuthGuard },
    { provide: APP_GUARD, useExisting: RateLimitGuard },
    { provide: APP_PIPE, useValue: createValidationPipe() },
    { provide: APP_FILTER, useClass: ApiExceptionFilter },
  ],
})
export class AppModule {}
