import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
  Req,
} from '@nestjs/common';
import {
  ApiCreatedResponse,
  ApiNoContentResponse,
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiResponse,
  ApiSecurity,
  ApiTags,
} from '@nestjs/swagger';
import { ErrorResponse } from '../common/error.response';
import { AuthenticatedRequest } from '../common/partner-auth.guard';
import { CreateWebhookRequest, WebhookCreatedResponse, WebhookResponse } from './webhook.dto';
import { WebhooksService } from './webhooks.service';

@ApiTags('Webhooks')
@ApiSecurity('partnerApiKey')
@Controller('v1/webhooks')
export class WebhooksController {
  constructor(private readonly webhooks: WebhooksService) {}

  @Post()
  @ApiOperation({
    operationId: 'createWebhook',
    summary: 'Cadastrar webhook do parceiro',
    description:
      'O segredo HMAC é devolvido apenas nesta resposta. Cada parceiro só enxerga os próprios webhooks.',
  })
  @ApiCreatedResponse({
    type: WebhookCreatedResponse,
    description: 'Webhook criado; o segredo é devolvido apenas nesta resposta.',
  })
  @ApiResponse({ status: 400, type: ErrorResponse, description: 'Corpo inválido.' })
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
  create(
    @Body() body: CreateWebhookRequest,
    @Req() request: AuthenticatedRequest,
  ): Promise<WebhookCreatedResponse> {
    return this.webhooks.create(request.partner, body);
  }

  @Get()
  @ApiOperation({
    operationId: 'listWebhooks',
    summary: 'Listar webhooks do parceiro autenticado',
  })
  @ApiOkResponse({ type: [WebhookResponse], description: 'Webhooks do parceiro.' })
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
  list(@Req() request: AuthenticatedRequest): Promise<WebhookResponse[]> {
    return this.webhooks.list(request.partner);
  }

  @Delete(':webhookId')
  @HttpCode(204)
  @ApiOperation({
    operationId: 'deleteWebhook',
    summary: 'Remover webhook do parceiro autenticado',
  })
  @ApiParam({ name: 'webhookId', format: 'uuid' })
  @ApiNoContentResponse({ description: 'Webhook removido.' })
  @ApiResponse({
    status: 401,
    type: ErrorResponse,
    description: 'Credencial ausente ou inválida.',
  })
  @ApiResponse({
    status: 404,
    type: ErrorResponse,
    description: 'Webhook não encontrado para o parceiro autenticado.',
  })
  @ApiResponse({
    status: 429,
    type: ErrorResponse,
    description: 'Limite de requisições excedido.',
  })
  async remove(
    @Param('webhookId', new ParseUUIDPipe()) webhookId: string,
    @Req() request: AuthenticatedRequest,
  ): Promise<void> {
    await this.webhooks.remove(request.partner, webhookId);
  }
}
