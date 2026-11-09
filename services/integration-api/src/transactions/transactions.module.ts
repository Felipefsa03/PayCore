import { Module } from '@nestjs/common';
import { FinancialCoreModule } from '../financial-core/financial-core.module';
import { TransactionsController } from './transactions.controller';
import { TransactionsService } from './transactions.service';

@Module({
  imports: [FinancialCoreModule],
  controllers: [TransactionsController],
  providers: [TransactionsService],
})
export class TransactionsModule {}
