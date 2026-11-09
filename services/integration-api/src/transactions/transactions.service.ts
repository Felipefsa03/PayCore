import { Injectable } from '@nestjs/common';
import { AuthenticatedRequest } from '../common/partner-auth.guard';
import { FinancialCoreClient } from '../financial-core/financial-core.module';
import { TransactionResponse } from './transaction.response';

@Injectable()
export class TransactionsService {
  constructor(private readonly client: FinancialCoreClient) {}

  async get(
    request: AuthenticatedRequest,
    transactionId: string,
  ): Promise<TransactionResponse> {
    const partner = request.partner;
    const transaction = await this.client.getTransfer(
      partner.ownerId,
      transactionId,
      request.correlationId ?? '',
    );
    return transaction;
  }
}
