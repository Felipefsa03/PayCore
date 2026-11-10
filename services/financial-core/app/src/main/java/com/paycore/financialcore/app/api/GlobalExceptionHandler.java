package com.paycore.financialcore.app.api;

import com.paycore.financialcore.accounts.AccountAccessDeniedException;
import com.paycore.financialcore.accounts.AccountNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduz exceções de domínio e de validação para o formato {@link ApiError}
 * com códigos estáveis do contrato (RF-01, RF-02).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> handleApiException(ApiException exception) {
    return ResponseEntity.status(exception.getStatus())
        .body(
            new ApiError(
                exception.getCode(), exception.getMessage(), exception.getDetails()));
  }

  @ExceptionHandler(com.paycore.financialcore.app.idempotency.IdempotencyKeyConflictException.class)
  ResponseEntity<ApiError> handleKeyConflict() {
    return error(HttpStatus.CONFLICT, "idempotency_key_conflict", "Chave reutilizada com dados diferentes.");
  }

  @ExceptionHandler(com.paycore.financialcore.app.idempotency.IdempotencyKeyInProgressException.class)
  ResponseEntity<ApiError> handleKeyBusy() {
    return error(HttpStatus.CONFLICT, "idempotency_key_in_progress", "Transferência em andamento.");
  }

  @ExceptionHandler(AccountNotFoundException.class)
  ResponseEntity<ApiError> handleNotFound(AccountNotFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "account_not_found", "Conta não encontrada.");
  }

  @ExceptionHandler(AccountAccessDeniedException.class)
  ResponseEntity<ApiError> handleAccessDenied(AccountAccessDeniedException exception) {
    return error(
        HttpStatus.FORBIDDEN,
        "forbidden",
        "Esta conta não pertence ao proprietário autenticado.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException exception) {
    Map<String, Object> fields = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(field -> fields.put(field.getField(), field.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                "validation_error", "Requisição inválida.", Map.of("fields", fields)));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException exception) {
    List<String> messages =
        exception.getAllValidationResults().stream()
            .flatMap(result -> result.getResolvableErrors().stream())
            .map(MessageSourceResolvable::getDefaultMessage)
            .toList();
    return validationError(messages);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
    List<String> messages =
        exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .toList();
    return validationError(messages);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ApiError> handleMalformedRequest(Exception exception) {
    return error(HttpStatus.BAD_REQUEST, "validation_error", "Requisição inválida.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception) {
    return error(HttpStatus.NOT_FOUND, "not_found", "Recurso não encontrado.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleUnexpected(Exception exception) {
    LOG.error("Erro inesperado ao processar requisição", exception);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal_error",
        "Erro interno inesperado. Consulte o correlation id nos logs.");
  }

  private static ResponseEntity<ApiError> validationError(List<String> messages) {
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                "validation_error",
                "Requisição inválida.",
                messages.isEmpty() ? null : Map.of("errors", messages)));
  }

  private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ApiError.of(code, message));
  }
}
