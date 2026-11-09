import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class ErrorResponse {
  @ApiProperty({ example: 'validation_error' })
  code!: string;

  @ApiProperty({ example: 'Requisição inválida.' })
  message!: string;

  @ApiPropertyOptional({
    type: 'object',
    additionalProperties: true,
    description: 'Detalhes específicos do erro (ex.: campos inválidos).',
  })
  details?: Record<string, unknown>;

  @ApiProperty({
    format: 'uuid',
    description: 'Correlação da requisição, também devolvida em X-Correlation-Id.',
  })
  correlationId!: string;
}
