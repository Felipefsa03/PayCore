package com.paycore.financialcore.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento de domínio persistido na mesma transação da mudança de estado (RF-06).
 *
 * <p>O outbox é a fonte da verdade da publicação: o evento só deixa de ser
 * {@link Status#PENDING} depois que o publicador confirma o envio. Se o broker
 * estiver indisponível, a linha permanece pendente e é reprocessada com
 * backoff exponencial; a semântica é at-least-once.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  public enum Status {
    PENDING,
    PUBLISHED,
    FAILED
  }

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, updatable = false, length = 120)
  private String eventType;

  @Column(name = "event_version", nullable = false, updatable = false)
  private int eventVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "correlation_id", nullable = false, updatable = false)
  private UUID correlationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected OutboxEvent() {
    // required by JPA
  }

  public OutboxEvent(
      UUID id,
      String aggregateType,
      UUID aggregateId,
      String eventType,
      int eventVersion,
      String payload,
      UUID correlationId) {
    if (aggregateType == null || aggregateType.isBlank()) {
      throw new IllegalArgumentException("aggregateType must not be blank");
    }
    if (aggregateId == null) {
      throw new IllegalArgumentException("aggregateId must not be null");
    }
    if (eventType == null || eventType.isBlank()) {
      throw new IllegalArgumentException("eventType must not be blank");
    }
    if (eventVersion <= 0) {
      throw new IllegalArgumentException("eventVersion must be positive");
    }
    if (payload == null || payload.isBlank()) {
      throw new IllegalArgumentException("payload must not be blank");
    }
    if (correlationId == null) {
      throw new IllegalArgumentException("correlationId must not be null");
    }
    this.id = id;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.eventVersion = eventVersion;
    this.payload = payload;
    this.correlationId = correlationId;
    this.status = Status.PENDING;
    this.attempts = 0;
    Instant now = Instant.now();
    this.availableAt = now;
    this.createdAt = now;
  }

  /** Registra a publicação confirmada pelo publicador. */
  public void markPublished(Instant when) {
    if (status == Status.PUBLISHED) {
      throw new IllegalStateException("outbox event already published: " + id);
    }
    this.status = Status.PUBLISHED;
    this.publishedAt = when;
    this.lastError = null;
  }

  /**
   * Registra uma tentativa de publicação que falhou e agenda a próxima.
   *
   * <p>Após {@code maxAttempts} tentativas o evento vira {@link Status#FAILED},
   * preservando o erro para inspeção e reprocessamento manual. O prazo de
   * retry é calculado pelo publicador (backoff exponencial).
   */
  public void markRetry(Instant nextAttemptAt, String error, int maxAttempts) {
    if (status == Status.PUBLISHED) {
      throw new IllegalStateException("outbox event already published: " + id);
    }
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("maxAttempts must be positive");
    }
    if (nextAttemptAt == null) {
      throw new IllegalArgumentException("nextAttemptAt must not be null");
    }
    this.attempts = this.attempts + 1;
    this.availableAt = nextAttemptAt;
    this.lastError = truncate(error);
    this.status = attempts >= maxAttempts ? Status.FAILED : Status.PENDING;
  }

  private static String truncate(String error) {
    if (error == null) {
      return "unknown publisher error";
    }
    return error.length() <= 2_000 ? error : error.substring(0, 2_000);
  }

  public UUID getId() {
    return id;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getEventType() {
    return eventType;
  }

  public int getEventVersion() {
    return eventVersion;
  }

  public String getPayload() {
    return payload;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  public Status getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getAvailableAt() {
    return availableAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
