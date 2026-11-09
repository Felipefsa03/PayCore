import { Logger } from '@nestjs/common';
import { createApplication } from './app.factory';
import { APP_CONFIG, AppConfig } from './config/app-config';
import { setupOpenApi } from './openapi/openapi';

async function bootstrap(): Promise<void> {
  const app = await createApplication();
  setupOpenApi(app);
  const config = app.get<AppConfig>(APP_CONFIG);
  await app.listen(config.port, '0.0.0.0');
  Logger.log(
    `integration-api ouvindo em http://127.0.0.1:${config.port} (docs em /docs)`,
    'Bootstrap',
  );
}

void bootstrap();
