import { ValidationError, ValidationPipe } from '@nestjs/common';
import { ApiError } from './api-error';

export function createValidationPipe(): ValidationPipe {
  return new ValidationPipe({
    whitelist: true,
    forbidNonWhitelisted: true,
    transform: true,
    exceptionFactory: (errors: ValidationError[]) =>
      new ApiError(400, 'validation_error', 'Requisição inválida.', {
        fields: flatten(errors),
      }),
  });
}

function flatten(
  errors: ValidationError[],
  parent = '',
): Array<{ field: string; errors: string[] }> {
  const result: Array<{ field: string; errors: string[] }> = [];
  for (const error of errors) {
    const field = parent ? `${parent}.${error.property}` : error.property;
    if (error.constraints) {
      result.push({ field, errors: Object.values(error.constraints) });
    }
    if (error.children && error.children.length > 0) {
      result.push(...flatten(error.children, field));
    }
  }
  return result;
}
