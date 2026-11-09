import { Controller, Get, Param, ParseUUIDPipe, Req } from '@nestjs/common';
import {
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiResponse,
  ApiSecurity,
  ApiTags,
} from '@nestjs/swagger';
import { ErrorResponse } from '../common/error.response';
import { AuthenticatedRequest } from '../common/partner-auth.guard';
import { TransactionsService } from './transactions.service';
import { TransactionResponse } from './transaction.response';

@ApiTags('Transactions')
@ApiSecurity('partnerApiKey')
@Controller('v1/transactions')
export class TransactionsController {
  constructor(private readonly transactions: TransactionsService) {}

  @Get(':transactionId')
  @ApiOperation({
    operationId: 'getTransaction',
    summary: 'Consultar uma transação do parceiro e seu estado',
    description:
      'Consulta o núcleo financeiro com um JWT de curta duração do proprietário do parceiro. ' +
      'Transações de outro parceiro retornam 404.',
  })
  @ApiParam({ name: 'transactionId', format: 'uuid' })
  @ApiOkResponse({ type: TransactionResponse, description: 'Transação encontrada.' })
  @ApiResponse({
    status: 400,
    type: ErrorResponse,
    description: 'Identificador de transação inválido.',
  })
  @ApiResponse({
    status: 401,
    type: ErrorResponse,
    description: 'Credencial ausente ou inválida.',
  })
  @ApiResponse({
    status: 404,
    type: ErrorResponse,
    description: 'Transação não encontrada para o parceiro autenticado.',
  })
  @ApiResponse({
    status: 429,
    type: ErrorResponse,
    description: 'Limite de requisições excedido.',
  })
  @ApiResponse({
    status: 503,
    type: ErrorResponse,
    description: 'Núcleo financeiro indisponível.',
  })
  get(
    @Param('transactionId', new ParseUUIDPipe()) transactionId: string,
    @Req() request: AuthenticatedRequest,
  ): Promise<TransactionResponse> {
    return this.transactions.get(request, transactionId);
  }
}
