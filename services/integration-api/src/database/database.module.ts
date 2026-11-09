import { Global, Inject, Injectable, Module, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { Pool } from 'pg';
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

  table(name: string): string {
    return `"${this.schema}"."${name}"`;
  }
}

function migrations(schema: string): string[] {
  const table = `"${schema}".webhook_subscriptions`;
  return [
    `CREATE TABLE IF NOT EXISTS ${table} (
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
      ON ${table} (partner_id, created_at DESC)`,
  ];
}

@Global()
@Module({
  providers: [DatabaseService],
  exports: [DatabaseService],
})
export class DatabaseModule {}
