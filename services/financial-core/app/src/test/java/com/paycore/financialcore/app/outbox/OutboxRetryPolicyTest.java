package com.paycore.financialcore.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

  @Test
  void growsExponentiallyFromTheBase() {
    assertThat(OutboxRetryPolicy.delayMillis(1, 1_000L, 300_000L)).isEqualTo(1_000L);
    assertThat(OutboxRetryPolicy.delayMillis(2, 1_000L, 300_000L)).isEqualTo(2_000L);
    assertThat(OutboxRetryPolicy.delayMillis(5, 1_000L, 300_000L)).isEqualTo(16_000L);
  }

  @Test
  void respectsTheMaximumDelay() {
    assertThat(OutboxRetryPolicy.delayMillis(20, 1_000L, 300_000L)).isEqualTo(300_000L);
    assertThat(OutboxRetryPolicy.delayMillis(1_000, 1_000L, 60_000L)).isEqualTo(60_000L);
  }

  @Test
  void neverReturnsLessThanOneMillisecond() {
    assertThat(OutboxRetryPolicy.delayMillis(1, 0L, 0L)).isEqualTo(1L);
  }

  @Test
  void rejectsNonPositiveAttempts() {
    assertThatThrownBy(() -> OutboxRetryPolicy.delayMillis(0, 1_000L, 10_000L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
