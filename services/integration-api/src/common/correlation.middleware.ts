import { randomUUID } from 'node:crypto';
import { NextFunction, Request, Response } from 'express';

const CORRELATION_PATTERN = /^[A-Za-z0-9._:-]{1,100}$/;

/**
 * Aceita `X-Correlation-Id` somente com caracteres seguros e tamanho limitado;
 * caso contrário gera um UUID. O valor é devolvido na resposta e propagado ao
 * núcleo financeiro.
 */
export function correlationMiddleware(request: Request, response: Response, next: NextFunction) {
  const incoming = request.header('x-correlation-id');
  const correlationId =
    incoming && CORRELATION_PATTERN.test(incoming) ? incoming : randomUUID();
  (request as Request & { correlationId: string }).correlationId = correlationId;
  response.setHeader('X-Correlation-Id', correlationId);
  next();
}
