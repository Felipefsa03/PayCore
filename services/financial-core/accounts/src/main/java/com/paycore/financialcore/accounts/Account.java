package com.paycore.financialcore.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Conta financeira simulada (RF-01, RF-02). O saldo disponível não é um campo
 * desta entidade: é uma projeção derivada dos postings efetivados do ledger
 * (docs/architecture/modelo-dominio-estados-transferencia.md §1.1).
 */
@Entity
@Table(name = "accounts")
public class Account {

  /** Define se a conta aceita saldo negativo (invariante I-05). */
  public enum OverdraftPolicy {
    FORBIDDEN,
    ALLOWED
  }

  /** Conta encerrada não aceita novas transferências. */
  public enum Status {
    ACTIVE,
    CLOSED
  }

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private UUID ownerId;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "overdraft_policy", nullable = false)
  private OverdraftPolicy overdraftPolicy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Account() {
    // required by JPA
  }

  public Account(UUID id, UUID ownerId, String currency, OverdraftPolicy overdraftPolicy) {
    this.id = id;
    this.ownerId = ownerId;
    this.currency = currency;
    this.overdraftPolicy = overdraftPolicy;
    this.status = Status.ACTIVE;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getCurrency() {
    return currency;
  }

  public OverdraftPolicy getOverdraftPolicy() {
    return overdraftPolicy;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
