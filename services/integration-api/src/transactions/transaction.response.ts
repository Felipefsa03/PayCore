import { ApiProperty } from '@nestjs/swagger';

export class TransactionResponse {
  @ApiProperty({ format: 'uuid' })
  id!: string;

  @ApiProperty({
    enum: [
      'RECEIVED',
      'VALIDATING',
      'PROCESSING',
      'COMPLETED',
      'REJECTED',
      'FAILED',
    ],
  })
  state!: string;

  @ApiProperty({ type: 'integer', minimum: 1, description: 'Valor em centavos' })
  amount!: number;

  @ApiProperty({ pattern: '^[A-Z]{3}$', example: 'BRL' })
  currency!: string;

  @ApiProperty({ format: 'uuid' })
  sourceAccountId!: string;

  @ApiProperty({ format: 'uuid' })
  destinationAccountId!: string;

  @ApiProperty({ type: String, nullable: true })
  failureReason!: string | null;

  @ApiProperty({ type: String, format: 'uuid', nullable: true })
  journalEntryId!: string | null;

  @ApiProperty({ format: 'uuid', description: 'Correlação original da transferência' })
  correlationId!: string;

  @ApiProperty({ format: 'date-time' })
  createdAt!: string;

  @ApiProperty({ format: 'date-time' })
  updatedAt!: string;
}
