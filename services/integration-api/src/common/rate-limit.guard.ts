import { CanActivate, ExecutionContext, Inject, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { ApiError, PartnerRequest } from './api-error';
import { IS_PUBLIC } from './public.decorator';

interface Bucket {
  timestamps: number[];
}

/**
 * Limite por parceiro e rota, em janela deslizante. Uma instância única de
 * laboratório usa memória local; réplicas exigiriam contador compartilhado.
 */
@Injectable()
export class RateLimitGuard implements CanActivate {
  private readonly buckets = new Map<string, Bucket>();

  constructor(
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    private readonly reflector: Reflector,
  ) {}

  canActivate(context: ExecutionContext): boolean {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (isPublic) {
      return true;
    }
    const request = context.switchToHttp().getRequest<PartnerRequest>();
    const partner = request.partner;
    if (!partner) {
      return true;
    }
    const route = `${context.getClass().name}.${context.getHandler().name}`;
    const key = `${partner.id}:${route}`;
    const now = Date.now();
    const windowMs = this.config.rateLimit.windowMs;
    const bucket = this.buckets.get(key) ?? { timestamps: [] };
    bucket.timestamps = bucket.timestamps.filter((timestamp) => now - timestamp < windowMs);
    if (bucket.timestamps.length >= partner.rateLimitPerMinute) {
      const retryAfterSeconds = Math.max(
        1,
        Math.ceil((windowMs - (now - bucket.timestamps[0])) / 1000),
      );
      this.buckets.set(key, bucket);
      throw new ApiError(
        429,
        'rate_limited',
        'Limite de requisições excedido para este parceiro.',
        { retryAfterSeconds },
      );
    }
    bucket.timestamps.push(now);
    this.buckets.set(key, bucket);
    return true;
  }

  reset(): void {
    this.buckets.clear();
  }
}
