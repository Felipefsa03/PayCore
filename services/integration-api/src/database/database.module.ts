import { Global, Inject, Injectable, Module, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { Pool, PoolClient } from 'pg';
import { APP_CONFIG, AppConfig } from '../config/app-config';

@Injectable()
export class DatabaseService implements OnModuleInit, OnModuleDestroy {
  private readonly pool: Pool;
  private readonly schema: string;

  constructor(@Inject(APP_CONFIG) config: AppConfig) {
    this.pool = new Pool({ connectionString: config.database.url, max: 5 });
    this.schema = config.database.schema;
  }

  async onModuleInit(): Promise<void> {
    await this.pool.query(`CREATE SCHEMA IF NOT EXISTS "${this.schema}"`);
    for (const statement of migrations(this.schema)) {
      await this.pool.query(statement);
    }
  }

  async onModuleDestroy(): Promise<void> {
    await this.pool.end();
  }

  async query<T extends Record<string, unknown>>(
    sql: string,
    params: unknown[] = [],
  ): Promise<T[]> {
    const result = await this.pool.query(sql, params);
    return result.rows as T[];
  }

  async execute(sql: string, params: unknown[] = []): Promise<number> {
    const result = await this.pool.query(sql, params);
    return result.rowCount ?? 0;
  }

  async transaction<T>(work: (client: PoolClient) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const result = await work(client);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  table(name: string): string {
    return `"${this.schema}"."${name}"`;
  }
}

function migrations(schema: string): string[] {
  const subscriptions = `"${schema}".webhook_subscriptions`;
  const deliveries = `"${schema}".webhook_deliveries`;
  const attempts = `"${schema}".webhook_delivery_attempts`;
  return [
    `CREATE TABLE IF NOT EXISTS ${subscriptions} (
      id uuid PRIMARY KEY,
      partner_id text NOT NULL,
      owner_id uuid NOT NULL,
      url text NOT NULL,
      event_types text[] NOT NULL,
      description text,
      active boolean NOT NULL DEFAULT true,
      secret text NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    )`,
    `CREATE INDEX IF NOT EXISTS webhook_subscriptions_partner_idx
      ON ${subscriptions} (partner_id, created_at DESC)`,
    `CREATE TABLE IF NOT EXISTS ${deliveries} (
      id uuid PRIMARY KEY,
      subscription_id uuid NOT NULL
        REFERENCES ${subscriptions} (id) ON DELETE CASCADE,
      partner_id text NOT NULL,
      event_id text NOT NULL,
      event_type text NOT NULL,
      body text NOT NULL,
      status text NOT NULL DEFAULT 'pending'
        CHECK (status IN ('pending', 'delivering', 'delivered', 'dead')),
      origin text NOT NULL DEFAULT 'event'
        CHECK (origin IN ('event', 'manual_resend')),
      source_delivery_id uuid
        REFERENCES ${deliveries} (id) ON DELETE SET NULL,
      resend_reason text,
      attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
      max_attempts integer NOT NULL DEFAULT 6 CHECK (max_attempts >= 1),
      next_attempt_at timestamptz NOT NULL DEFAULT now(),
      last_attempt_at timestamptz,
      last_status_code integer,
      last_error text,
      delivered_at timestamptz,
      created_at timestamptz NOT NULL DEFAULT now(),
      updated_at timestamptz NOT NULL DEFAULT now()
    )`,
    `CREATE UNIQUE INDEX IF NOT EXISTS webhook_deliveries_event_unique
      ON ${deliveries} (subscription_id, event_id) WHERE origin = 'event'`,
    `CREATE UNIQUE INDEX IF NOT EXISTS webhook_deliveries_pending_resend_unique
      ON ${deliveries} (source_delivery_id)
      WHERE origin = 'manual_resend' AND status IN ('pending', 'delivering')`,
    `CREATE INDEX IF NOT EXISTS webhook_deliveries_due_idx
      ON ${deliveries} (status, next_attempt_at)`,
    `CREATE INDEX IF NOT EXISTS webhook_deliveries_partner_idx
      ON ${deliveries} (partner_id, created_at DESC)`,
    `CREATE INDEX IF NOT EXISTS webhook_deliveries_subscription_idx
      ON ${deliveries} (subscription_id, created_at DESC)`,
    `CREATE TABLE IF NOT EXISTS ${attempts} (
      id uuid PRIMARY KEY,
      delivery_id uuid NOT NULL
        REFERENCES ${deliveries} (id) ON DELETE CASCADE,
      attempt_number integer NOT NULL CHECK (attempt_number >= 1),
      outcome text NOT NULL CHECK (
        outcome IN ('succeeded', 'http_error', 'timeout', 'network_error', 'blocked_destination')
      ),
      response_status integer,
      error text,
      duration_ms integer CHECK (duration_ms IS NULL OR duration_ms >= 0),
      request_timestamp bigint NOT NULL,
      signature text NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now(),
      UNIQUE (delivery_id, attempt_number)
    )`,
    `CREATE INDEX IF NOT EXISTS webhook_delivery_attempts_delivery_idx
      ON ${attempts} (delivery_id, attempt_number)`,
  ];
}

@Global()
@Module({
  providers: [DatabaseService],
  exports: [DatabaseService],
})
export class DatabaseModule {}
