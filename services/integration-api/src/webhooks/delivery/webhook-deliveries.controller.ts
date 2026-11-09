import {
  Body,
  Controller,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
  Query,
  Req,
} from '@nestjs/common';
import {
  ApiAcceptedResponse,
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiResponse,
  ApiSecurity,
  ApiTags,
} from '@nestjs/swagger';
import { ErrorResponse } from '../../common/error.response';
import { AuthenticatedRequest } from '../../common/partner-auth.guard';
import {
  ListWebhookDeliveriesQuery,
  ResendWebhookDeliveryRequest,
  WebhookDeliveryDetailResponse,
  WebhookDeliveryListResponse,
  WebhookDeliveryResponse,
} from './webhook-delivery.dto';
import { WebhookDeliveriesService } from './webhook-deliveries.service';

@ApiTags('Webhook deliveries')
@ApiSecurity('partnerApiKey')
@Controller('v1/webhook-deliveries')
export class WebhookDeliveriesController {
  constructor(private readonly deliveries: WebhookDeliveriesService) {}

  @Get()
  @ApiOperation({
    operationId: 'listWebhookDeliveries',
    summary: 'Listar entregas de webhooks do parceiro autenticado',
    description:
      'O histórico inclui o estado atual, tentativas e a dead-letter queue (`status=dead`). ' +
      'Cada parceiro só enxerga as próprias entregas.',
  })
  @ApiOkResponse({
    type: WebhookDeliveryListResponse,
    description: 'Entregas do parceiro, mais recentes primeiro.',
  })
  @ApiResponse({
    status: 400,
    type: ErrorResponse,
    description: 'Filtros inválidos.',
  })
  @ApiResponse({
    status: 401,
    type: ErrorResponse,
    description: 'Credencial ausente ou inválida.',
  })
  @ApiResponse({
    status: 429,
    type: ErrorResponse,
    description: 'Limite de requisições excedido.',
  })
  list(
    @Query() query: ListWebhookDeliveriesQuery,
    @Req() request: AuthenticatedRequest,
  ): Promise<WebhookDeliveryListResponse> {
    return this.deliveries.list(request.partner, query);
  }

  @Get(':deliveryId')
  @ApiOperation({
    operationId: 'getWebhookDelivery',
    summary: 'Consultar uma entrega e suas tentativas',
  })
  @ApiParam({ name: 'deliveryId', format: 'uuid' })
  @ApiOkResponse({
    type: WebhookDeliveryDetailResponse,
    description: 'Entrega com payload assinado e tentativas auditáveis.',
  })
  @ApiResponse({
    status: 401,
    type: ErrorResponse,
    description: 'Credencial ausente ou inválida.',
  })
  @ApiResponse({
    status: 404,
    type: ErrorResponse,
    description: 'Entrega não encontrada para o parceiro autenticado.',
  })
  @ApiResponse({
    status: 429,
    type: ErrorResponse,
    description: 'Limite de requisições excedido.',
  })
  detail(
    @Param('deliveryId', new ParseUUIDPipe()) deliveryId: string,
    @Req() request: AuthenticatedRequest,
  ): Promise<WebhookDeliveryDetailResponse> {
    return this.deliveries.detail(request.partner, deliveryId);
  }

  @Post(':deliveryId/resend')
  @HttpCode(202)
  @ApiOperation({
    operationId: 'resendWebhookDelivery',
    summary: 'Reenviar manualmente uma entrega da dead-letter queue',
    description:
      'Cria uma nova entrega vinculada à original e com o mesmo payload assinado. ' +
      'Cada reenvio fica auditável; o evento original não é reexecutado.',
  })
  @ApiParam({ name: 'deliveryId', format: 'uuid' })
  @ApiAcceptedResponse({
    type: WebhookDeliveryResponse,
    description: 'Reenvio enfileirado.',
  })
  @ApiResponse({
    status: 401,
    type: ErrorResponse,
    description: 'Credencial ausente ou inválida.',
  })
  @ApiResponse({
    status: 404,
    type: ErrorResponse,
    description: 'Entrega não encontrada para o parceiro autenticado.',
  })
  @ApiResponse({
    status: 409,
    type: ErrorResponse,
    description: 'Entrega não está na DLQ ou já possui reenvio pendente.',
  })
  @ApiResponse({
    status: 429,
    type: ErrorResponse,
    description: 'Limite de requisições excedido.',
  })
  resend(
    @Param('deliveryId', new ParseUUIDPipe()) deliveryId: string,
    @Body() body: ResendWebhookDeliveryRequest,
    @Req() request: AuthenticatedRequest,
  ): Promise<WebhookDeliveryResponse> {
    return this.deliveries.resend(request.partner, deliveryId, body);
  }
}
