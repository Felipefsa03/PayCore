package com.paycore.financialcore.app.outbox;

/**
 * Cálculo do atraso entre tentativas de publicação.
 *
 * <p>Backoff exponencial com teto: base * 2^(attempt-1), limitado a
 * {@code maxMillis}. Isolado em classe própria para ser testado sem banco e
 * sem relógio.
 */
public final class OutboxRetryPolicy {

  private OutboxRetryPolicy() {
    // utility class
  }

  public static long delayMillis(int attempt, long baseMillis, long maxMillis) {
    if (attempt <= 0) {
      throw new IllegalArgumentException("attempt must be positive");
    }
    long base = Math.max(1L, baseMillis);
    long cap = Math.max(base, maxMillis);
    int exponent = Math.min(attempt - 1, 30);
    double exponential = base * Math.pow(2.0, exponent);
    return (long) Math.min((double) cap, exponential);
  }
}
