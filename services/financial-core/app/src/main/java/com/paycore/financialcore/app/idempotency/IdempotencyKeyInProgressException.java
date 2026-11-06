package com.paycore.financialcore.app.idempotency;

/** Operação com a mesma chave ainda está em processamento. */
public class IdempotencyKeyInProgressException extends IllegalStateException {
  public IdempotencyKeyInProgressException() {
    super("idempotency key is still being processed");
  }
}
