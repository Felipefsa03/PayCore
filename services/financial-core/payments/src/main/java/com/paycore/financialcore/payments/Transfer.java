package com.paycore.financialcore.payments;

import com.paycore.financialcore.shared.TransferState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Transferência entre contas internas (RF-03). A máquina de estados e suas
 * transições válidas estão documentadas em docs/architecture/
 * modelo-dominio-estados-transferencia.md; esta entidade é apenas a
 * persistência do estado atual. A efetivação atômica e o controle de
 * concorrência são implementados no card dedicado a transferências atômicas.
 */
@Entity
@Table(name = "transfers")
public class Transfer {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "source_account_id", nullable = false, updatable = false)
  private UUID sourceAccountId;

  @Column(name = "destination_account_id", nullable = false, updatable = false)
  private UUID destinationAccountId;

  @Column(nullable = false, updatable = false)
  private long amount;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "idempotency_key", nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "correlation_id", nullable = false, updatable = false)
  private UUID correlationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransferState state;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "journal_entry_id")
  private UUID journalEntryId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Transfer() {
    // required by JPA
  }

  public Transfer(
      UUID id,
      UUID sourceAccountId,
      UUID destinationAccountId,
      long amount,
      String currency,
      String idempotencyKey,
      UUID correlationId) {
    if (sourceAccountId.equals(destinationAccountId)) {
      throw new IllegalArgumentException("source and destination accounts must differ");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
    this.id = id;
    this.sourceAccountId = sourceAccountId;
    this.destinationAccountId = destinationAccountId;
    this.amount = amount;
    this.currency = currency;
    this.idempotencyKey = idempotencyKey;
    this.correlationId = correlationId;
    this.state = TransferState.RECEIVED;
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSourceAccountId() {
    return sourceAccountId;
  }

  public UUID getDestinationAccountId() {
    return destinationAccountId;
  }

  public long getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }

  public TransferState getState() {
    return state;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public UUID getJournalEntryId() {
    return journalEntryId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
