package com.paycore.financialcore.app.risk;

import java.time.Instant;

public class CircuitBreaker {
  private final int failureThreshold;
  private final long openMillis;
  private int failures;
  private Instant openedAt;

  public CircuitBreaker(int failureThreshold, long openMillis) {
    this.failureThreshold = failureThreshold;
    this.openMillis = openMillis;
  }

  public synchronized boolean allowRequest() {
    if (openedAt == null) {
      return true;
    }
    if (Instant.now().isAfter(openedAt.plusMillis(openMillis))) {
      openedAt = null;
      failures = 0;
      return true;
    }
    return false;
  }

  public synchronized void recordSuccess() {
    failures = 0;
    openedAt = null;
  }

  public synchronized void recordFailure() {
    failures++;
    if (failures >= failureThreshold) {
      openedAt = Instant.now();
    }
  }
}
