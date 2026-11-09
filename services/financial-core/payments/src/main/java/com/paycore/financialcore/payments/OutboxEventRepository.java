package com.paycore.financialcore.payments;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /**
   * Reserva um lote de eventos pendentes e vencidos.
   *
   * <p>{@code FOR UPDATE SKIP LOCKED} permite que várias instâncias do
   * publicador rodem ao mesmo tempo: cada uma trava um subconjunto diferente e
   * nunca publica a mesma linha concorrentemente.
   */
  @Query(
      value =
          """
          SELECT * FROM outbox_events
           WHERE status = 'PENDING' AND available_at <= now()
           ORDER BY available_at, created_at
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<OutboxEvent> claimPending(@Param("limit") int limit);

  List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);

  List<OutboxEvent> findByCorrelationId(UUID correlationId);
}
