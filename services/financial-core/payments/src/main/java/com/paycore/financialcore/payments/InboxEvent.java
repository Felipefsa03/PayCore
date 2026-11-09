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
 * Evento recebido do Kafka, persistido para deduplicação e replay (RF-07).
 *
 * <p>{@code eventId} é único: uma entrega repetida não cria nova linha nem
 * reexecuta o efeito financeiro. O payload guarda o envelope completo para que
 * o replay não dependa do broker.
 */
@Entity
@Table(name = "inbox_events")
public class InboxEvent {

  public enum Status {
    RECEIVED,
    PROCESSED,
    FAILED
  }

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "event_type", nullable = false, updatable = false, length = 120)
  private String eventType;

  @Column(name = "event_version", nullable = false, updatable = false)
  private int eventVersion;

  @Column(name = "aggregate_id", nullable = false, updatable = false)
  private UUID aggregateId;

  @Column(nullable = false, updatable = false, length = 200)
  private String topic;

  @Column(name = "kafka_partition", nullable = false, updatable = false)
  private int partition;

  @Column(name = "kafka_offset", nullable = false, updatable = false)
  private long offset;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected InboxEvent() {
    // required by JPA
  }

  public InboxEvent(
      UUID id,
      UUID eventId,
      String eventType,
      int eventVersion,
      UUID aggregateId,
      String topic,
      int partition,
      long offset,
      String payload,
      Instant receivedAt) {
    this.id = id;
    this.eventId = eventId;
    this.eventType = eventType;
    this.eventVersion = eventVersion;
    this.aggregateId = aggregateId;
    this.topic = topic;
    this.partition = partition;
    this.offset = offset;
    this.payload = payload;
    this.status = Status.RECEIVED;
    this.attempts = 0;
    this.receivedAt = receivedAt;
  }

  public void markProcessed(Instant at) {
    this.status = Status.PROCESSED;
    this.processedAt = at;
    this.lastError = null;
  }

  public void markFailed(String error, Instant at) {
    this.status = Status.FAILED;
    this.attempts++;
    this.lastError = error;
    this.processedAt = at;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public int getEventVersion() {
    return eventVersion;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public String getTopic() {
    return topic;
  }

  public int getPartition() {
    return partition;
  }

  public long getOffset() {
    return offset;
  }

  public String getPayload() {
    return payload;
  }

  public Status getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
