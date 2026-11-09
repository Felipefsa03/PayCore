import { createHmac } from 'node:crypto';
import { Inject, Injectable, Module } from '@nestjs/common';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { ApiError } from '../common/api-error';

export interface TransactionView {
  id: string;
  state: string;
  amount: number;
  currency: string;
  sourceAccountId: string;
  destinationAccountId: string;
  failureReason: string | null;
  journalEntryId: string | null;
  correlationId: string;
  createdAt: string;
  updatedAt: string;
}

interface UpstreamTransfer {
  id?: unknown;
  state?: unknown;
  amount?: unknown;
  currency?: unknown;
  sourceAccountId?: unknown;
  destinationAccountId?: unknown;
  failureReason?: unknown;
  journalEntryId?: unknown;
  correlationId?: unknown;
  createdAt?: unknown;
  updatedAt?: unknown;
}

@Injectable()
export class FinancialCoreClient {
  constructor(@Inject(APP_CONFIG) private readonly config: AppConfig) {}

  async getTransfer(
    ownerId: string,
    transactionId: string,
    correlationId: string,
  ): Promise<TransactionView> {
    const url = `${this.config.financialCore.baseUrl}/transfers/${encodeURIComponent(transactionId)}`;
    let response: Response;
    try {
      response = await fetch(url, {
        method: 'GET',
        headers: {
          accept: 'application/json',
          authorization: `Bearer ${this.issueToken(ownerId)}`,
          'x-correlation-id': correlationId,
        },
        cache: 'no-store',
        signal: AbortSignal.timeout(this.config.financialCore.timeoutMs),
      });
    } catch {
      throw new ApiError(503, 'financial_core_unavailable', 'Núcleo financeiro indisponível.');
    }
    if (response.status === 404) {
      throw new ApiError(404, 'transaction_not_found', 'Transação não encontrada.');
    }
    if (response.status === 401 || response.status === 403) {
      throw new ApiError(
        502,
        'upstream_auth_error',
        'Falha de autenticação com o núcleo financeiro.',
      );
    }
    if (response.status !== 200) {
      throw new ApiError(503, 'financial_core_unavailable', 'Núcleo financeiro indisponível.');
    }
    let body: UpstreamTransfer;
    try {
      body = (await response.json()) as UpstreamTransfer;
    } catch {
      throw new ApiError(502, 'invalid_upstream_response', 'Resposta inválida do núcleo financeiro.');
    }
    return mapTransfer(body);
  }

  /**
   * JWT HS256 de curta duração com `sub` = ownerId. O núcleo financeiro valida
   * emissor, expiração e formato UUID do proprietário.
   */
  issueToken(ownerId: string): string {
    const now = Math.floor(Date.now() / 1000);
    const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = base64url(
      JSON.stringify({
        iss: this.config.auth.jwtIssuer,
        sub: ownerId,
        iat: now,
        exp: now + 60,
      }),
    );
    const data = `${header}.${payload}`;
    const signature = createHmac('sha256', this.config.auth.jwtSecret)
      .update(data)
      .digest('base64url');
    return `${data}.${signature}`;
  }
}

function mapTransfer(body: UpstreamTransfer): TransactionView {
  if (
    typeof body.id !== 'string' ||
    typeof body.state !== 'string' ||
    typeof body.amount !== 'number' ||
    typeof body.currency !== 'string' ||
    typeof body.sourceAccountId !== 'string' ||
    typeof body.destinationAccountId !== 'string' ||
    typeof body.createdAt !== 'string' ||
    typeof body.updatedAt !== 'string'
  ) {
    throw new ApiError(502, 'invalid_upstream_response', 'Resposta inválida do núcleo financeiro.');
  }
  return {
    id: body.id,
    state: body.state,
    amount: body.amount,
    currency: body.currency,
    sourceAccountId: body.sourceAccountId,
    destinationAccountId: body.destinationAccountId,
    failureReason: typeof body.failureReason === 'string' ? body.failureReason : null,
    journalEntryId: typeof body.journalEntryId === 'string' ? body.journalEntryId : null,
    correlationId: typeof body.correlationId === 'string' ? body.correlationId : '',
    createdAt: body.createdAt,
    updatedAt: body.updatedAt,
  };
}

function base64url(value: string): string {
  return Buffer.from(value, 'utf8').toString('base64url');
}

@Module({
  providers: [FinancialCoreClient],
  exports: [FinancialCoreClient],
})
export class FinancialCoreModule {}
