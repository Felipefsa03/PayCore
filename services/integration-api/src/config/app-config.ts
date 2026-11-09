import { createHash } from 'node:crypto';

export const APP_CONFIG = Symbol('APP_CONFIG');

export interface PartnerConfig {
  readonly id: string;
  readonly name: string;
  readonly ownerId: string;
  readonly apiKeyHash: string;
  readonly rateLimitPerMinute: number;
}

export interface WebhookConfig {
  readonly workerEnabled: boolean;
  readonly pollIntervalMs: number;
  readonly batchSize: number;
  readonly maxAttempts: number;
  readonly timeoutMs: number;
  readonly backoffBaseMs: number;
  readonly backoffMaxMs: number;
  readonly lockTtlMs: number;
  readonly allowPrivateNetworks: boolean;
  readonly kafka: {
    readonly enabled: boolean;
    readonly brokers: readonly string[];
    readonly groupId: string;
    readonly clientId: string;
  };
}

export interface AppConfig {
  readonly port: number;
  readonly partners: readonly PartnerConfig[];
  readonly auth: { readonly jwtSecret: string; readonly jwtIssuer: string };
  readonly financialCore: { readonly baseUrl: string; readonly timeoutMs: number };
  readonly database: { readonly url: string; readonly schema: string };
  readonly rateLimit: { readonly windowMs: number; readonly defaultPerMinute: number };
  readonly webhooks: WebhookConfig;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PARTNER_ID_PATTERN = /^[a-z0-9][a-z0-9_-]{1,63}$/;
const SCHEMA_PATTERN = /^[a-z_][a-z0-9_]{0,62}$/;

export class ConfigError extends Error {}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const port = integer(env.PORT, 3000, 'PORT', 1, 65_535);
  const allowEmptyPartners = env.PAYCORE_ALLOW_EMPTY_PARTNERS === '1';
  const defaultRateLimit = integer(
    env.PAYCORE_RATE_LIMIT_PER_MINUTE,
    120,
    'PAYCORE_RATE_LIMIT_PER_MINUTE',
    1,
    100_000,
  );
  const jwtSecret =
    env.PAYCORE_AUTH_JWT_SECRET ?? 'paycore-lab-dev-only-secret-change-me-0123456789abcdef';
  if (Buffer.byteLength(jwtSecret, 'utf8') < 32) {
    throw new ConfigError('PAYCORE_AUTH_JWT_SECRET deve ter ao menos 32 bytes para HS256');
  }
  const jwtIssuer = env.PAYCORE_AUTH_JWT_ISSUER ?? 'paycore-lab';
  const databaseUrl = env.PAYCORE_INTEGRATION_DB_URL;
  if (!databaseUrl) {
    throw new ConfigError('PAYCORE_INTEGRATION_DB_URL é obrigatória');
  }
  const schema = env.PAYCORE_INTEGRATION_DB_SCHEMA ?? 'integration_api';
  if (!SCHEMA_PATTERN.test(schema)) {
    throw new ConfigError('PAYCORE_INTEGRATION_DB_SCHEMA inválido');
  }
  const financialCoreUrl = env.PAYCORE_FINANCIAL_CORE_URL ?? 'http://127.0.0.1:8080';
  let parsedBaseUrl: URL;
  try {
    parsedBaseUrl = new URL(financialCoreUrl);
  } catch {
    throw new ConfigError('PAYCORE_FINANCIAL_CORE_URL inválida');
  }
  if (parsedBaseUrl.protocol !== 'http:' && parsedBaseUrl.protocol !== 'https:') {
    throw new ConfigError('PAYCORE_FINANCIAL_CORE_URL deve usar http ou https');
  }
  const timeoutMs = integer(
    env.PAYCORE_FINANCIAL_CORE_TIMEOUT_MS,
    3_000,
    'PAYCORE_FINANCIAL_CORE_TIMEOUT_MS',
    100,
    60_000,
  );
  const partners = parsePartners(env.PAYCORE_PARTNERS, defaultRateLimit);
  if (partners.length === 0 && !allowEmptyPartners) {
    throw new ConfigError('PAYCORE_PARTNERS precisa conter ao menos um parceiro');
  }
  return {
    port,
    partners,
    auth: { jwtSecret, jwtIssuer },
    financialCore: { baseUrl: financialCoreUrl.replace(/\/+$/, ''), timeoutMs },
    database: { url: databaseUrl, schema },
    rateLimit: { windowMs: 60_000, defaultPerMinute: defaultRateLimit },
    webhooks: loadWebhookConfig(env),
  };
}

function loadWebhookConfig(env: NodeJS.ProcessEnv): WebhookConfig {
  const workerEnabled = boolean(env.PAYCORE_WEBHOOK_WORKER_ENABLED, true, 'PAYCORE_WEBHOOK_WORKER_ENABLED');
  const kafkaEnabled = boolean(env.PAYCORE_WEBHOOK_KAFKA_ENABLED, false, 'PAYCORE_WEBHOOK_KAFKA_ENABLED');
  const allowPrivateNetworks = boolean(
    env.PAYCORE_WEBHOOK_ALLOW_PRIVATE_NETWORKS,
    false,
    'PAYCORE_WEBHOOK_ALLOW_PRIVATE_NETWORKS',
  );
  const backoffBaseMs = integer(
    env.PAYCORE_WEBHOOK_BACKOFF_BASE_MS,
    1_000,
    'PAYCORE_WEBHOOK_BACKOFF_BASE_MS',
    1,
    60_000,
  );
  const backoffMaxMs = integer(
    env.PAYCORE_WEBHOOK_BACKOFF_MAX_MS,
    60_000,
    'PAYCORE_WEBHOOK_BACKOFF_MAX_MS',
    1,
    3_600_000,
  );
  if (backoffMaxMs < backoffBaseMs) {
    throw new ConfigError('PAYCORE_WEBHOOK_BACKOFF_MAX_MS precisa ser >= BASE_MS');
  }
  const brokers = (env.PAYCORE_WEBHOOK_KAFKA_BROKERS ?? '127.0.0.1:9092')
    .split(',')
    .map((broker) => broker.trim())
    .filter((broker) => broker.length > 0);
  if (brokers.length === 0) {
    throw new ConfigError('PAYCORE_WEBHOOK_KAFKA_BROKERS precisa conter ao menos um broker');
  }
  const groupId = env.PAYCORE_WEBHOOK_KAFKA_GROUP_ID ?? 'paycore-integration-webhooks';
  if (groupId.trim().length === 0) {
    throw new ConfigError('PAYCORE_WEBHOOK_KAFKA_GROUP_ID inválido');
  }
  return {
    workerEnabled,
    pollIntervalMs: integer(
      env.PAYCORE_WEBHOOK_POLL_INTERVAL_MS,
      1_000,
      'PAYCORE_WEBHOOK_POLL_INTERVAL_MS',
      50,
      600_000,
    ),
    batchSize: integer(env.PAYCORE_WEBHOOK_BATCH_SIZE, 10, 'PAYCORE_WEBHOOK_BATCH_SIZE', 1, 100),
    maxAttempts: integer(
      env.PAYCORE_WEBHOOK_MAX_ATTEMPTS,
      6,
      'PAYCORE_WEBHOOK_MAX_ATTEMPTS',
      1,
      20,
    ),
    timeoutMs: integer(
      env.PAYCORE_WEBHOOK_TIMEOUT_MS,
      5_000,
      'PAYCORE_WEBHOOK_TIMEOUT_MS',
      100,
      60_000,
    ),
    backoffBaseMs,
    backoffMaxMs,
    lockTtlMs: integer(
      env.PAYCORE_WEBHOOK_LOCK_TTL_MS,
      30_000,
      'PAYCORE_WEBHOOK_LOCK_TTL_MS',
      1_000,
      600_000,
    ),
    allowPrivateNetworks,
    kafka: {
      enabled: kafkaEnabled,
      brokers,
      groupId,
      clientId: env.PAYCORE_WEBHOOK_KAFKA_CLIENT_ID ?? 'paycore-integration-api',
    },
  };
}

function parsePartners(raw: string | undefined, defaultRateLimit: number): PartnerConfig[] {
  if (!raw) {
    return [];
  }
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new ConfigError('PAYCORE_PARTNERS precisa ser JSON válido');
  }
  if (!Array.isArray(value)) {
    throw new ConfigError('PAYCORE_PARTNERS precisa ser um array');
  }
  const partners: PartnerConfig[] = [];
  const ids = new Set<string>();
  const keys = new Set<string>();
  const owners = new Set<string>();
  for (const item of value) {
    if (typeof item !== 'object' || item === null || Array.isArray(item)) {
      throw new ConfigError('Cada parceiro precisa ser um objeto');
    }
    const record = item as Record<string, unknown>;
    const id = requiredString(record.id, 'id', 64);
    const name = requiredString(record.name, 'name', 120);
    const apiKey = requiredString(record.apiKey, 'apiKey', 200);
    const ownerId = requiredString(record.ownerId, 'ownerId', 36);
    if (!PARTNER_ID_PATTERN.test(id)) {
      throw new ConfigError(`Parceiro com id inválido: ${id}`);
    }
    if (!UUID_PATTERN.test(ownerId)) {
      throw new ConfigError(`Parceiro ${id} com ownerId inválido`);
    }
    if (apiKey.length < 8) {
      throw new ConfigError(`Parceiro ${id} com apiKey curta`);
    }
    const rateLimit =
      record.rateLimitPerMinute === undefined
        ? defaultRateLimit
        : integerValue(record.rateLimitPerMinute, 1, 100_000);
    const apiKeyHash = hashApiKey(apiKey);
    if (ids.has(id) || keys.has(apiKeyHash) || owners.has(ownerId)) {
      throw new ConfigError('Parceiros precisam ter id, apiKey e ownerId únicos');
    }
    ids.add(id);
    keys.add(apiKeyHash);
    owners.add(ownerId);
    partners.push({ id, name, ownerId, apiKeyHash, rateLimitPerMinute: rateLimit });
  }
  return partners;
}

export function hashApiKey(apiKey: string): string {
  return createHash('sha256').update(apiKey, 'utf8').digest('hex');
}

function requiredString(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== 'string' || value.trim().length === 0 || value.length > maxLength) {
    throw new ConfigError(`Parceiro com ${field} inválido`);
  }
  return value;
}

function integer(
  raw: string | undefined,
  fallback: number,
  name: string,
  min: number,
  max: number,
): number {
  if (raw === undefined || raw === '') {
    return fallback;
  }
  const parsed = Number.parseInt(raw, 10);
  if (!Number.isInteger(parsed) || String(parsed) !== raw.trim()) {
    throw new ConfigError(`${name} precisa ser um inteiro`);
  }
  if (parsed < min || parsed > max) {
    throw new ConfigError(`${name} fora do intervalo permitido`);
  }
  return parsed;
}

function boolean(raw: string | undefined, fallback: boolean, name: string): boolean {
  if (raw === undefined || raw === '') {
    return fallback;
  }
  if (raw === '1' || raw.toLowerCase() === 'true') {
    return true;
  }
  if (raw === '0' || raw.toLowerCase() === 'false') {
    return false;
  }
  throw new ConfigError(`${name} precisa ser 1/0 ou true/false`);
}

function integerValue(value: unknown, min: number, max: number): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < min || value > max) {
    throw new ConfigError('rateLimitPerMinute inválido');
  }
  return value;
}
