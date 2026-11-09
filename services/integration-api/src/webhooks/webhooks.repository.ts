import { Injectable } from '@nestjs/common';
import { DatabaseService } from '../database/database.module';

export interface WebhookRecord {
  id: string;
  partnerId: string;
  ownerId: string;
  url: string;
  eventTypes: string[];
  description: string | null;
  active: boolean;
  secret: string;
  createdAt: Date;
  updatedAt: Date;
}

interface WebhookRow extends Record<string, unknown> {
  id: string;
  partner_id: string;
  owner_id: string;
  url: string;
  event_types: string[];
  description: string | null;
  active: boolean;
  secret: string;
  created_at: Date;
  updated_at: Date;
}

@Injectable()
export class WebhooksRepository {
  private readonly table: string;

  constructor(private readonly database: DatabaseService) {
    this.table = database.table('webhook_subscriptions');
  }

  async create(record: WebhookRecord): Promise<WebhookRecord> {
    const rows = await this.database.query<WebhookRow>(
      `INSERT INTO ${this.table}
        (id, partner_id, owner_id, url, event_types, description, active, secret)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
       RETURNING *`,
      [
        record.id,
        record.partnerId,
        record.ownerId,
        record.url,
        record.eventTypes,
        record.description,
        record.active,
        record.secret,
      ],
    );
    return mapRow(rows[0]);
  }

  async listByPartner(partnerId: string): Promise<WebhookRecord[]> {
    const rows = await this.database.query<WebhookRow>(
      `SELECT * FROM ${this.table}
       WHERE partner_id = $1
       ORDER BY created_at DESC, id ASC`,
      [partnerId],
    );
    return rows.map(mapRow);
  }

  async deleteById(partnerId: string, id: string): Promise<boolean> {
    const deleted = await this.database.execute(
      `DELETE FROM ${this.table} WHERE partner_id = $1 AND id = $2`,
      [partnerId, id],
    );
    return deleted > 0;
  }
}

function mapRow(row: WebhookRow): WebhookRecord {
  return {
    id: row.id,
    partnerId: row.partner_id,
    ownerId: row.owner_id,
    url: row.url,
    eventTypes: row.event_types,
    description: row.description,
    active: row.active,
    secret: row.secret,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}
