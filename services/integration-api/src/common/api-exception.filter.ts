import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  Logger,
} from '@nestjs/common';
import { Response } from 'express';
import { randomUUID } from 'node:crypto';
import { ApiError, PartnerRequest } from './api-error';

interface ErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  correlationId: string;
}

@Catch()
export class ApiExceptionFilter implements ExceptionFilter {
  private readonly logger = new Logger(ApiExceptionFilter.name);

  catch(exception: unknown, host: ArgumentsHost): void {
    const context = host.switchToHttp();
    const response = context.getResponse<Response>();
    const request = context.getRequest<PartnerRequest>();
    const correlationId = request.correlationId ?? randomUUID();
    const body = toErrorBody(exception, correlationId);
    if (body.status === 429) {
      response.setHeader('Retry-After', String(body.retryAfterSeconds ?? 1));
    }
    response.setHeader('X-Correlation-Id', correlationId);
    const level = body.status >= 500 ? 'error' : 'warn';
    this.logger[level](
      JSON.stringify({
        event: 'request_failed',
        correlationId,
        method: request.method,
        path: request.originalUrl ?? request.path,
        status: body.status,
        code: body.code,
      }),
    );
    response.status(body.status).json({
      code: body.code,
      message: body.message,
      ...(body.details ? { details: body.details } : {}),
      correlationId,
    });
  }
}

interface ResolvedError {
  status: number;
  code: string;
  message: string;
  details?: Record<string, unknown>;
  retryAfterSeconds?: number;
}

function toErrorBody(exception: unknown, correlationId: string): ResolvedError & ErrorBody {
  if (exception instanceof ApiError) {
    return {
      status: exception.status,
      code: exception.code,
      message: exception.message,
      details: exception.details,
      retryAfterSeconds:
        typeof exception.details?.retryAfterSeconds === 'number'
          ? exception.details.retryAfterSeconds
          : undefined,
      correlationId,
    };
  }
  if (exception instanceof HttpException) {
    const status = exception.getStatus();
    const payload = exception.getResponse();
    const message =
      typeof payload === 'object' && payload !== null && 'message' in payload
        ? String((payload as { message: unknown }).message)
        : exception.message;
    return {
      status,
      code: status === 400 ? 'validation_error' : status === 404 ? 'not_found' : 'http_error',
      message: status === 400 ? 'Requisição inválida.' : message,
      correlationId,
    };
  }
  return {
    status: 500,
    code: 'internal_error',
    message: 'Erro interno.',
    correlationId,
  };
}
