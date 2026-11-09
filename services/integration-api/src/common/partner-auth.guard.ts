import { timingSafeEqual } from 'node:crypto';
import { CanActivate, ExecutionContext, Inject, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { APP_CONFIG, AppConfig, hashApiKey, PartnerConfig } from '../config/app-config';
import { ApiError, PartnerRequest } from './api-error';
import { IS_PUBLIC } from './public.decorator';

@Injectable()
export class PartnerAuthGuard implements CanActivate {
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
    const apiKey = request.header('x-api-key');
    if (!apiKey) {
      throw new ApiError(401, 'unauthenticated', 'Credencial ausente ou inválida.');
    }
    const candidate = Buffer.from(hashApiKey(apiKey), 'hex');
    for (const partner of this.config.partners) {
      if (safeEquals(candidate, Buffer.from(partner.apiKeyHash, 'hex'))) {
        request.partner = partner;
        return true;
      }
    }
    throw new ApiError(401, 'unauthenticated', 'Credencial ausente ou inválida.');
  }
}

export type AuthenticatedRequest = PartnerRequest & { partner: PartnerConfig };

function safeEquals(left: Buffer, right: Buffer): boolean {
  return left.length === right.length && timingSafeEqual(left, right);
}
