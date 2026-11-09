package com.paycore.financialcore.payments;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementEntryRepository extends JpaRepository<SettlementEntry, UUID> {

  /**
   * Cria a liquidação apenas uma vez por pagamento.
   *
   * <p>O conflito cobre tanto {@code payment_id} quanto {@code event_id}:
   * reprocessar o mesmo evento, ou um evento diferente do mesmo pagamento, não
   * duplica o efeito financeiro.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO settlements
            (id, payment_id, event_id, amount, currency, settled_at, created_at)
          VALUES
            (:id, :paymentId, :eventId, :amount, :currency, :settledAt, :createdAt)
          ON CONFLICT DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("paymentId") UUID paymentId,
      @Param("eventId") UUID eventId,
      @Param("amount") long amount,
      @Param("currency") String currency,
      @Param("settledAt") Instant settledAt,
      @Param("createdAt") Instant createdAt);

  Optional<SettlementEntry> findByPaymentId(UUID paymentId);
}
