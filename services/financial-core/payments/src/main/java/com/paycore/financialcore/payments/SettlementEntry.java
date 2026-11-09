package com.paycore.financialcore.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Efeito financeiro do consumidor: pagamento confirmado vira uma liquidação.
 *
 * <p>{@code paymentId} e {@code eventId} são únicos no banco. Reprocessar o
 * mesmo evento (replay ou entrega repetida) não cria uma segunda liquidação.
 */
@Entity
@Table(name = "settlements")
public class SettlementEntry {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "payment_id", nullable = false, updatable = false)
  private UUID paymentId;

  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(nullable = false, updatable = false)
  private long amount;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "settled_at", nullable = false, updatable = false)
  private Instant settledAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected SettlementEntry() {
    // required by JPA
  }

  public SettlementEntry(
      UUID id,
      UUID paymentId,
      UUID eventId,
      long amount,
      String currency,
      Instant settledAt,
      Instant createdAt) {
    this.id = id;
    this.paymentId = paymentId;
    this.eventId = eventId;
    this.amount = amount;
    this.currency = currency;
    this.settledAt = settledAt;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public long getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getSettledAt() {
    return settledAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
