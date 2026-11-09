import { createHmac, timingSafeEqual } from 'node:crypto';
import { createServer, IncomingMessage, Server, ServerResponse } from 'node:http';
import { AddressInfo } from 'node:net';

export interface StubTransfer {
  id: string;
  ownerId: string;
  payload: Record<string, unknown>;
}

export interface UpstreamRequest {
  transactionId: string;
  sub: string;
  correlationId: string | undefined;
}

/**
 * Simula o núcleo financeiro: valida a assinatura HS256 e o emissor do JWT,
 * aplica isolamento por `sub` e registra a correlação recebida.
 */
export class StubFinancialCore {
  private server: Server | null = null;
  private port = 0;
  get boundPort(): number {
    return this.port;
  }
  readonly transfers = new Map<string, StubTransfer>();
  readonly requests: UpstreamRequest[] = [];
  baseUrl = '';

  async start(port = 0): Promise<void> {
    this.server = createServer((request, response) => this.handle(request, response));
    await new Promise<void>((resolve) => {
      this.server?.listen(port, '127.0.0.1', () => resolve());
    });
    const address = this.server.address() as AddressInfo;
    this.port = address.port;
    this.baseUrl = `http://127.0.0.1:${this.port}`;
  }

  async stop(): Promise<void> {
    const server = this.server;
    if (!server) {
      return;
    }
    await new Promise<void>((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()));
    });
    this.server = null;
  }

  seed(id: string, ownerId: string, overrides: Record<string, unknown> = {}): void {
    this.transfers.set(id, {
      id,
      ownerId,
      payload: {
        id,
        sourceAccountId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        destinationAccountId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        amount: 400,
        currency: 'BRL',
        state: 'COMPLETED',
        failureReason: null,
        journalEntryId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        correlationId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
        createdAt: '2026-11-01T12:00:00Z',
        updatedAt: '2026-11-01T12:00:01Z',
        ...overrides,
      },
    });
  }

  private handle(request: IncomingMessage, response: ServerResponse): void {
    const path = (request.url ?? '/').split('?')[0];
    const match = /^\/transfers\/([0-9a-f-]{36})$/.exec(path);
    const authorization = request.headers.authorization;
    if (!match || !authorization?.startsWith('Bearer ')) {
      this.json(response, 401, { code: 'unauthenticated', message: 'Credencial ausente.' });
      return;
    }
    const sub = this.verifyToken(authorization.slice('Bearer '.length));
    if (!sub) {
      this.json(response, 401, { code: 'unauthenticated', message: 'Token inválido.' });
      return;
    }
    const transactionId = match[1];
    const correlationId =
      typeof request.headers['x-correlation-id'] === 'string'
        ? request.headers['x-correlation-id']
        : undefined;
    this.requests.push({ transactionId, sub, correlationId });
    const transfer = this.transfers.get(transactionId);
    if (!transfer || transfer.ownerId !== sub) {
      this.json(response, 404, {
        code: 'transfer_not_found',
        message: 'Transferência não encontrada.',
      });
      return;
    }
    this.json(response, 200, transfer.payload);
  }

  private verifyToken(token: string): string | null {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }
    const secret = process.env.PAYCORE_AUTH_JWT_SECRET ?? '';
    const expected = createHmac('sha256', secret).update(`${parts[0]}.${parts[1]}`).digest();
    const provided = Buffer.from(parts[2], 'base64url');
    if (expected.length !== provided.length || !timingSafeEqual(expected, provided)) {
      return null;
    }
    let payload: { sub?: unknown; iss?: unknown; exp?: unknown };
    try {
      payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8')) as {
        sub?: unknown;
        iss?: unknown;
        exp?: unknown;
      };
    } catch {
      return null;
    }
    const issuer = process.env.PAYCORE_AUTH_JWT_ISSUER ?? 'paycore-lab';
    if (payload.iss !== issuer || typeof payload.exp !== 'number') {
      return null;
    }
    if (payload.exp < Math.floor(Date.now() / 1000)) {
      return null;
    }
    return typeof payload.sub === 'string' ? payload.sub : null;
  }

  private json(response: ServerResponse, status: number, body: unknown): void {
    const serialized = JSON.stringify(body);
    response.writeHead(status, {
      'content-type': 'application/json',
      'content-length': Buffer.byteLength(serialized),
    });
    response.end(serialized);
  }
}
