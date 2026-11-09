package com.paycore.financialcore.app.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes operacionais do publicador do outbox.
 *
 * <p>Os valores padrão são conservadores para o laboratório: lotes pequenos,
 * polling de cinco segundos e backoff exponencial de 1s a 5min. Em produção os
 * valores devem ser calibrados pelo volume e pelo SLA do broker.
 */
@ConfigurationProperties(prefix = "paycore.outbox")
public class OutboxProperties {

  private int batchSize = 50;

  private long pollIntervalMs = 5_000;

  private long retryBaseMs = 1_000;

  private long retryMaxMs = 300_000;

  private int maxAttempts = 12;

  private boolean scheduled = true;

  /** Transporte do outbox: {@code log} (laboratório) ou {@code kafka}. */
  private String transport = "log";

  private long kafkaSendTimeoutMs = 5_000;

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public long getPollIntervalMs() {
    return pollIntervalMs;
  }

  public void setPollIntervalMs(long pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
  }

  public long getRetryBaseMs() {
    return retryBaseMs;
  }

  public void setRetryBaseMs(long retryBaseMs) {
    this.retryBaseMs = retryBaseMs;
  }

  public long getRetryMaxMs() {
    return retryMaxMs;
  }

  public void setRetryMaxMs(long retryMaxMs) {
    this.retryMaxMs = retryMaxMs;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public boolean isScheduled() {
    return scheduled;
  }

  public void setScheduled(boolean scheduled) {
    this.scheduled = scheduled;
  }

  public String getTransport() {
    return transport;
  }

  public void setTransport(String transport) {
    this.transport = transport;
  }

  public long getKafkaSendTimeoutMs() {
    return kafkaSendTimeoutMs;
  }

  public void setKafkaSendTimeoutMs(long kafkaSendTimeoutMs) {
    this.kafkaSendTimeoutMs = kafkaSendTimeoutMs;
  }
}
