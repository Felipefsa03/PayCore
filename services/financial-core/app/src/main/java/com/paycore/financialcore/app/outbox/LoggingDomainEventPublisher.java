package com.paycore.financialcore.app.outbox;

import com.paycore.financialcore.payments.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publicador de laboratório: escreve o evento no log em vez de enviar a um
 * broker. Serve como transporte padrão até o card de Kafka e torna a semântica
 * at-least-once observável nos testes e na execução local.
 */
public class LoggingDomainEventPublisher implements DomainEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisher.class);

  @Override
  public void publish(OutboxEvent event) {
    log.info(
        "outbox publish eventId={} type={} version={} aggregate={}:{} correlationId={} payload={}",
        event.getId(),
        event.getEventType(),
        event.getEventVersion(),
        event.getAggregateType(),
        event.getAggregateId(),
        event.getCorrelationId(),
        event.getPayload());
  }
}
