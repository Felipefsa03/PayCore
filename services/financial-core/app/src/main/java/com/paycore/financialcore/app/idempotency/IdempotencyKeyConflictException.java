package com.paycore.financialcore.app.idempotency;

/** Mesma chave reutilizada com payload diferente. */
public class IdempotencyKeyConflictException extends IllegalStateException {
  public IdempotencyKeyConflictException() {
    super("idempotency key reused with a different request payload");
  }
}
