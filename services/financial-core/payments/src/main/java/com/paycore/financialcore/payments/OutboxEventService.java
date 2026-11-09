package com.paycore.financialcore.payments;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra eventos de domínio no outbox transacional (RF-06).
 *
 * <p>O método {@link #append} participa da transação corrente
 * ({@code Propagation.REQUIRED}): quem chama — por exemplo o caso de uso de
 * transferência — garante que o evento e a mudança de estado sejam confirmados
 * ou revertidos juntos.
 */
@Service
public class OutboxEventService {

  private final OutboxEventRepository events;

  public OutboxEventService(OutboxEventRepository events) {
    this.events = events;
  }

  @Transactional
  public OutboxEvent append(
      String aggregateType,
      UUID aggregateId,
      String eventType,
      int eventVersion,
      String payload,
      UUID correlationId) {
    return events.save(
        new OutboxEvent(
            UUID.randomUUID(),
            aggregateType,
            aggregateId,
            eventType,
            eventVersion,
            payload,
            correlationId));
  }
}
