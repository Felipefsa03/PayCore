// Gera o contrato OpenAPI versionado a partir dos decorators do NestJS.
// `--check` falha quando o arquivo commitado está divergente (usado no CI).
process.env.PAYCORE_ALLOW_EMPTY_PARTNERS ??= '1';
process.env.PAYCORE_INTEGRATION_DB_URL ??= 'postgresql://unused@127.0.0.1:1/unused';
process.env.PAYCORE_PARTNERS ??= '[]';

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { stringify } from 'yaml';

async function main(): Promise<void> {
  const { createApplication } = await import('../src/app.factory');
  const { buildOpenApiDocument } = await import('../src/openapi/openapi');
  const app = await createApplication({ logger: false });
  try {
    const document = buildOpenApiDocument(app);
    const yamlText = stringify(document, { lineWidth: 0 });
    const target = resolve(__dirname, '../../../contracts/openapi/integration-api.yaml');
    if (process.argv.includes('--check')) {
      if (!existsSync(target) || readFileSync(target, 'utf8') !== yamlText) {
        console.error('OpenAPI divergente: rode `npm run openapi:generate` e commite o resultado.');
        process.exitCode = 1;
        return;
      }
      console.log('OpenAPI em sincronia com os decorators.');
      return;
    }
    writeFileSync(target, yamlText, 'utf8');
    console.log(`OpenAPI gerado em ${target}`);
  } finally {
    await app.close();
  }
}

void main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
