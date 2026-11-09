package com.paycore.financialcore.payments;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {

  /**
   * Insere o evento somente se ainda não existe.
   *
   * <p>{@code ON CONFLICT (event_id) DO NOTHING} é a deduplicação atômica entre
   * consumidores concorrentes e entregas repetidas: o retorno 0 significa que
   * outro consumidor (ou uma entrega anterior) já registrou o evento.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO inbox_events
            (id, event_id, event_type, event_version, aggregate_id, topic,
             kafka_partition, kafka_offset, payload, status, attempts, received_at)
          VALUES
            (:id, :eventId, :eventType, :eventVersion, :aggregateId, :topic,
             :partition, :offset, CAST(:payload AS jsonb), 'RECEIVED', 0, :receivedAt)
          ON CONFLICT (event_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("eventId") UUID eventId,
      @Param("eventType") String eventType,
      @Param("eventVersion") int eventVersion,
      @Param("aggregateId") UUID aggregateId,
      @Param("topic") String topic,
      @Param("partition") int partition,
      @Param("offset") long offset,
      @Param("payload") String payload,
      @Param("receivedAt") Instant receivedAt);

  Optional<InboxEvent> findByEventId(UUID eventId);

  long countByStatus(InboxEvent.Status status);
}
