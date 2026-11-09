import { createHmac, timingSafeEqual } from 'node:crypto';
import { createServer, IncomingMessage, Server, ServerResponse } from 'node:http';
import { AddressInfo } from 'node:net';

export type WebhookResponseMode = 'ok' | 'timeout' | number;

export interface ReceivedWebhook {
  path: string;
  headers: Record<string, string>;
  rawBody: string;
  payload: Record<string, unknown>;
  timestamp: string | null;
  signature: string | null;
  signatureValid: boolean;
  eventId: string | null;
  deliveryId: string | null;
  attempt: string | null;
  receivedAt: Date;
}

/**
 * Receptor HTTP de teste: valida a assinatura HMAC sobre `${timestamp}.${body}`
 * e permite enfileirar 200, 500 ou timeout para exercitar retry e DLQ.
 */
export class StubWebhookReceiver {
  private server: Server | null = null;
  private port = 0;
  private readonly modes: WebhookResponseMode[] = [];
  secret: string | null = null;
  readonly requests: ReceivedWebhook[] = [];

  get boundPort(): number {
    return this.port;
  }

  async start(port = 0): Promise<void> {
    this.server = createServer((request, response) => {
      void this.handle(request, response);
    });
    await new Promise<void>((resolve) => {
      this.server?.listen(port, '127.0.0.1', () => resolve());
    });
    const address = this.server.address() as AddressInfo;
    this.port = address.port;
  }

  async stop(): Promise<void> {
    const server = this.server;
    if (!server) {
      return;
    }
    await new Promise<void>((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()));
      server.closeAllConnections?.();
    });
    this.server = null;
  }

  url(path = '/hooks/alpha'): string {
    return `http://127.0.0.1:${this.port}${path}`;
  }

  enqueue(...modes: WebhookResponseMode[]): void {
    this.modes.push(...modes);
  }

  reset(): void {
    this.requests.length = 0;
    this.modes.length = 0;
    this.secret = null;
  }

  private async handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
    const path = (request.url ?? '/').split('?')[0];
    const rawBody = await readBody(request);
    const headers = normalizeHeaders(request);
    const timestamp = headers['x-paycore-timestamp'] ?? null;
    const signature = headers['x-paycore-signature'] ?? null;
    const signatureValid =
      timestamp !== null &&
      signature !== null &&
      this.secret !== null &&
      verifySignature(this.secret, timestamp, rawBody, signature);
    let payload: Record<string, unknown> = {};
    try {
      const parsed = JSON.parse(rawBody) as unknown;
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        payload = parsed as Record<string, unknown>;
      }
    } catch {
      // O teste verifica a assinatura do corpo bruto mesmo quando ele não é JSON.
    }
    this.requests.push({
      path,
      headers,
      rawBody,
      payload,
      timestamp,
      signature,
      signatureValid,
      eventId: headers['x-paycore-event-id'] ?? null,
      deliveryId: headers['x-paycore-delivery-id'] ?? null,
      attempt: headers['x-paycore-attempt'] ?? null,
      receivedAt: new Date(),
    });
    if (!signatureValid) {
      send(response, 401, { ok: false, error: 'invalid_signature' });
      return;
    }
    const mode = this.modes.shift() ?? 'ok';
    if (mode === 'timeout') {
      await delay(600);
      send(response, 200, { ok: true, delayed: true });
      return;
    }
    if (typeof mode === 'number') {
      send(response, mode, { ok: false, status: mode });
      return;
    }
    send(response, 200, { ok: true });
  }
}

export function verifySignature(
  secret: string,
  timestamp: string,
  body: string,
  signature: string,
): boolean {
  const expected = `v1=${createHmac('sha256', secret).update(`${timestamp}.${body}`).digest('hex')}`;
  const provided = Buffer.from(signature, 'utf8');
  const expectedBuffer = Buffer.from(expected, 'utf8');
  return (
    provided.length === expectedBuffer.length && timingSafeEqual(provided, expectedBuffer)
  );
}

function normalizeHeaders(request: IncomingMessage): Record<string, string> {
  const headers: Record<string, string> = {};
  for (const [name, value] of Object.entries(request.headers)) {
    if (typeof value === 'string') {
      headers[name.toLowerCase()] = value;
    } else if (Array.isArray(value)) {
      headers[name.toLowerCase()] = value.join(',');
    }
  }
  return headers;
}

function readBody(request: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    request.on('data', (chunk: Buffer) => chunks.push(chunk));
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    request.on('error', reject);
  });
}

function send(response: ServerResponse, status: number, body: unknown): void {
  const serialized = JSON.stringify(body);
  response.writeHead(status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(serialized),
  });
  response.end(serialized);
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
