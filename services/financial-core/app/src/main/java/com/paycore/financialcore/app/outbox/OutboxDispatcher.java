package com.paycore.financialcore.app.outbox;

import com.paycore.financialcore.payments.OutboxEvent;
import com.paycore.financialcore.payments.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica um lote de eventos pendentes do outbox.
 *
 * <p>O lote é reservado com {@code FOR UPDATE SKIP LOCKED} na mesma transação
 * que o publica, o que dá controle de concorrência entre instâncias. Sucesso
 * marca o evento como {@code PUBLISHED}; falha incrementa a tentativa e agenda
 * o retry com backoff exponencial, mantendo o evento pendente.
 *
 * <p>Semântica at-least-once: se o processo cair entre a publicação externa e
 * o commit, o evento continua pendente e será reenviado. Consumidores devem ser
 * idempotentes (card de inbox/deduplicação).
 */
@Service
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

  private final OutboxEventRepository events;
  private final DomainEventPublisher publisher;
  private final OutboxProperties properties;

  public OutboxDispatcher(
      OutboxEventRepository events, DomainEventPublisher publisher, OutboxProperties properties) {
    this.events = events;
    this.publisher = publisher;
    this.properties = properties;
  }

  /**
   * Reserva e publica até {@code batchSize} eventos pendentes.
   *
   * @return quantidade de eventos processados neste lote
   */
  @Transactional
  public int dispatchOnce() {
    List<OutboxEvent> batch = events.claimPending(properties.getBatchSize());
    if (batch.isEmpty()) {
      return 0;
    }
    Instant now = Instant.now();
    for (OutboxEvent event : batch) {
      try {
        publisher.publish(event);
        event.markPublished(now);
      } catch (RuntimeException exception) {
        long delay =
            OutboxRetryPolicy.delayMillis(
                event.getAttempts() + 1, properties.getRetryBaseMs(), properties.getRetryMaxMs());
        event.markRetry(now.plusMillis(delay), exception.getMessage(), properties.getMaxAttempts());
        log.warn(
            "outbox publish failed eventId={} attempt={} retryInMs={} error={}",
            event.getId(),
            event.getAttempts(),
            delay,
            exception.getMessage());
      }
    }
    events.saveAll(batch);
    return batch.size();
  }
}
