import { PartnerConfig } from '../config/app-config';

export interface PartnerRequest {
  partner?: PartnerConfig;
  correlationId?: string;
  method?: string;
  originalUrl?: string;
  path?: string;
  header(name: string): string | undefined;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details?: Record<string, unknown>,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
