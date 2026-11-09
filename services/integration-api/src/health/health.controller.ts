import { Controller, Get } from '@nestjs/common';
import { ApiOkResponse, ApiOperation, ApiTags } from '@nestjs/swagger';
import { Public } from '../common/public.decorator';

@ApiTags('Health')
@Controller()
export class HealthController {
  @Get('health')
  @Public()
  @ApiOperation({ operationId: 'health', summary: 'Verificação de liveness' })
  @ApiOkResponse({
    description: 'Serviço vivo.',
    schema: { example: { status: 'ok', service: 'integration-api' } },
  })
  health(): { status: string; service: string } {
    return { status: 'ok', service: 'integration-api' };
  }
}
