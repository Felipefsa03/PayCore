package com.paycore.financialcore.app.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

  public enum Status {
    PROCESSING,
    COMPLETED
  }

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Column(nullable = false, updatable = false, length = 64)
  private String operation;

  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
  private String idempotencyKey;

  @Column(nullable = false, updatable = false, length = 64)
  private String fingerprint;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(name = "transfer_id", updatable = true)
  private UUID transferId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_snapshot", columnDefinition = "jsonb")
  private String responseSnapshot;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  protected IdempotencyRecord() {
    // JPA
  }

  public IdempotencyRecord(
      UUID id,
      UUID ownerId,
      String operation,
      String idempotencyKey,
      String fingerprint,
      Instant expiresAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.operation = operation;
    this.idempotencyKey = idempotencyKey;
    this.fingerprint = fingerprint;
    this.status = Status.PROCESSING.name();
    this.createdAt = Instant.now();
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getOperation() {
    return operation;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getStatus() {
    return status;
  }

  public UUID getTransferId() {
    return transferId;
  }

  public String getResponseSnapshot() {
    return responseSnapshot;
  }

  public void complete(UUID transferId, String responseSnapshot) {
    this.status = Status.COMPLETED.name();
    this.transferId = transferId;
    this.responseSnapshot = responseSnapshot;
  }
}
