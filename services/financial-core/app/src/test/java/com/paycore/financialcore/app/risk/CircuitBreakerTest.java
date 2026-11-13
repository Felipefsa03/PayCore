package com.paycore.financialcore.app.risk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CircuitBreakerTest {
  @Test
  void opensAfterThresholdAndRecovers() {
    CircuitBreaker breaker = new CircuitBreaker(2, 1000);
    assertTrue(breaker.allowRequest());
    breaker.recordFailure();
    breaker.recordFailure();
    assertFalse(breaker.allowRequest());
    breaker.recordSuccess();
    assertTrue(breaker.allowRequest());
  }
}
