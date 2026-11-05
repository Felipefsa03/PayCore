package com.paycore.financialcore.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Débito ou crédito individual, em centavos, vinculado a uma conta e a um
 * registro de diário (RFC-001 §5). Imutável após criação (invariante I-03).
 */
@Entity
@Table(name = "postings")
public class Posting {

  /** O sinal do lançamento vem desta direção, nunca do valor armazenado. */
  public enum Direction {
    DEBIT,
    CREDIT
  }

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "journal_entry_id", nullable = false, updatable = false)
  private JournalEntry journalEntry;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private Direction direction;

  @Column(nullable = false, updatable = false)
  private long amount;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Posting() {
    // required by JPA
  }

  public Posting(
      UUID id, JournalEntry journalEntry, UUID accountId, Direction direction, long amount,
      String currency) {
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
    this.id = id;
    this.journalEntry = journalEntry;
    this.accountId = accountId;
    this.direction = direction;
    this.amount = amount;
    this.currency = currency;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public JournalEntry getJournalEntry() {
    return journalEntry;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public Direction getDirection() {
    return direction;
  }

  public long getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
