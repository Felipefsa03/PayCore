import { PARTNERS_ENV } from './helpers/partners';

process.env.PAYCORE_ALLOW_EMPTY_PARTNERS = '0';
process.env.PAYCORE_AUTH_JWT_SECRET ??=
  'paycore-lab-dev-only-secret-change-me-0123456789abcdef';
process.env.PAYCORE_AUTH_JWT_ISSUER ??= 'paycore-lab';
process.env.PAYCORE_INTEGRATION_DB_SCHEMA ??= 'integration_api_test';
process.env.PAYCORE_FINANCIAL_CORE_URL ??= 'http://127.0.0.1:9';
process.env.PAYCORE_RATE_LIMIT_PER_MINUTE ??= '1000';
process.env.PAYCORE_PARTNERS = JSON.stringify(PARTNERS_ENV);
process.env.PAYCORE_WEBHOOK_WORKER_ENABLED = '0';
process.env.PAYCORE_WEBHOOK_ALLOW_PRIVATE_NETWORKS = '1';
process.env.PAYCORE_WEBHOOK_TIMEOUT_MS = '250';
process.env.PAYCORE_WEBHOOK_MAX_ATTEMPTS = '2';
process.env.PAYCORE_WEBHOOK_BACKOFF_BASE_MS = '10';
process.env.PAYCORE_WEBHOOK_BACKOFF_MAX_MS = '100';
process.env.PAYCORE_WEBHOOK_KAFKA_ENABLED = '0';

if (!process.env.PAYCORE_INTEGRATION_DB_URL) {
  throw new Error(
    'Defina PAYCORE_INTEGRATION_DB_URL para um PostgreSQL de teste (nunca o banco da aplicação).',
  );
}
