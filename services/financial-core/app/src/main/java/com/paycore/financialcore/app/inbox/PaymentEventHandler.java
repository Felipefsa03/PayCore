package com.paycore.financialcore.app.inbox;

import com.paycore.financialcore.payments.SettlementEntryRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Efeito financeiro do consumidor de eventos.
 *
 * <p>Pagamento confirmado gera uma liquidação única por {@code payment_id}.
 * Pagamento rejeitado não tem efeito financeiro. O processamento é idempotente:
 * repetir o mesmo evento não duplica a liquidação.
 */
@Component
public class PaymentEventHandler {

  public static final String SETTLED = "SETTLED";

  public static final String ALREADY_SETTLED = "ALREADY_SETTLED";

  public static final String NO_FINANCIAL_EFFECT = "NO_FINANCIAL_EFFECT";

  private final SettlementEntryRepository settlements;

  public PaymentEventHandler(SettlementEntryRepository settlements) {
    this.settlements = settlements;
  }

  public String process(InboundPaymentEvent event) {
    return switch (event.eventType()) {
      case "payment.completed" -> settle(event);
      case "payment.rejected" -> NO_FINANCIAL_EFFECT;
      default -> throw new IllegalArgumentException(
          "unsupported payment event type: " + event.eventType());
    };
  }

  private String settle(InboundPaymentEvent event) {
    Instant now = Instant.now();
    int inserted =
        settlements.insertIfAbsent(
            UUID.randomUUID(),
            event.aggregateId(),
            event.eventId(),
            event.amount(),
            event.currency(),
            event.occurredAt(),
            now);
    return inserted == 1 ? SETTLED : ALREADY_SETTLED;
  }
}
