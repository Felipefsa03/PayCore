import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  ArrayNotEmpty,
  ArrayUnique,
  IsArray,
  IsIn,
  IsOptional,
  IsString,
  IsUrl,
  Matches,
  MaxLength,
} from 'class-validator';
import { SUPPORTED_EVENT_TYPES } from './event-types';

export class CreateWebhookRequest {
  @ApiProperty({
    example: 'https://parceiro.example/webhooks/paycore',
    maxLength: 2048,
    description: 'URL HTTP(S) que receberá as notificações assinadas.',
  })
  @IsUrl({ protocols: ['http', 'https'], require_protocol: true, require_tld: false })
  @Matches(/^https?:\/\/[^/@\s]+(\/|$|\?|#)/i, {
    message: 'url deve usar http(s) e não pode conter credenciais',
  })
  @MaxLength(2048)
  url!: string;

  @ApiProperty({
    enum: [...SUPPORTED_EVENT_TYPES],
    isArray: true,
    example: ['payment.completed', 'payment.rejected'],
  })
  @IsArray()
  @ArrayNotEmpty()
  @ArrayUnique()
  @IsIn([...SUPPORTED_EVENT_TYPES], { each: true })
  eventTypes!: string[];

  @ApiPropertyOptional({ maxLength: 200, example: 'Webhook do parceiro Alpha' })
  @IsOptional()
  @IsString()
  @MaxLength(200)
  description?: string;
}

export class WebhookResponse {
  @ApiProperty({ format: 'uuid' })
  id!: string;

  @ApiProperty({ example: 'https://parceiro.example/webhooks/paycore' })
  url!: string;

  @ApiProperty({ enum: [...SUPPORTED_EVENT_TYPES], isArray: true })
  eventTypes!: string[];

  @ApiProperty({ type: String, nullable: true, maxLength: 200 })
  description!: string | null;

  @ApiProperty()
  active!: boolean;

  @ApiProperty({ format: 'date-time' })
  createdAt!: string;

  @ApiProperty({ format: 'date-time' })
  updatedAt!: string;
}

export class WebhookCreatedResponse extends WebhookResponse {
  @ApiProperty({
    description:
      'Segredo HMAC devolvido apenas na criação. Use-o para verificar a assinatura das entregas.',
    example: 'whsec_exemplo',
  })
  secret!: string;
}
