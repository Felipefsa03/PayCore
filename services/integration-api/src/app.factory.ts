import 'reflect-metadata';
import { INestApplication, LoggerService } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { correlationMiddleware } from './common/correlation.middleware';

export async function createApplication(options: {
  logger?: LoggerService | false | Array<'error' | 'warn' | 'log'>;
} = {}): Promise<INestApplication> {
  const app = await NestFactory.create(AppModule, {
    logger: options.logger ?? ['error', 'warn', 'log'],
  });
  app.use(correlationMiddleware);
  app.enableShutdownHooks();
  return app;
}
