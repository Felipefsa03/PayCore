import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { AppModule } from '../../src/app.module';
import { RateLimitGuard } from '../../src/common/rate-limit.guard';
import { correlationMiddleware } from '../../src/common/correlation.middleware';
import { DatabaseService } from '../../src/database/database.module';

export async function createTestApp(financialCoreUrl: string): Promise<INestApplication> {
  process.env.PAYCORE_FINANCIAL_CORE_URL = financialCoreUrl;
  const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
  const app = moduleRef.createNestApplication({ logger: false });
  app.use(correlationMiddleware);
  await app.init();
  return app;
}

export async function resetDatabase(app: INestApplication): Promise<void> {
  const database = app.get(DatabaseService);
  await database.execute(`TRUNCATE TABLE ${database.table('webhook_subscriptions')}`);
}

export function resetRateLimits(app: INestApplication): void {
  app.get(RateLimitGuard).reset();
}
