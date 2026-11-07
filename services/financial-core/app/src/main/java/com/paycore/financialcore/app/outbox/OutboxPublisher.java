package com.paycore.financialcore.app.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o publicador do outbox periodicamente.
 *
 * <p>Pode ser desligado com {@code paycore.outbox.scheduled=false} — os testes
 * de integração usam isso para controlar o momento exato da publicação.
 */
@Component
@ConditionalOnProperty(
    prefix = "paycore.outbox",
    name = "scheduled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxDispatcher dispatcher;

  public OutboxPublisher(OutboxDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Scheduled(fixedDelayString = "${paycore.outbox.poll-interval-ms:5000}")
  public void poll() {
    try {
      int processed = dispatcher.dispatchOnce();
      if (processed > 0) {
        log.debug("outbox dispatched {} event(s)", processed);
      }
    } catch (RuntimeException exception) {
      // O próximo ciclo tenta de novo; o evento permanece no banco.
      log.error("outbox dispatch cycle failed", exception);
    }
  }
}
