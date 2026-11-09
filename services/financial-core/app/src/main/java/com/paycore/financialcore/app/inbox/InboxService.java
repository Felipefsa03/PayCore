package com.paycore.financialcore.app.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountRepository;
import com.paycore.financialcore.app.api.ApiException;
import com.paycore.financialcore.payments.InboxEvent;
import com.paycore.financialcore.payments.InboxEventRepository;
import com.paycore.financialcore.payments.InboxReplay;
import com.paycore.financialcore.payments.InboxReplayRepository;
import com.paycore.financialcore.payments.Transfer;
import com.paycore.financialcore.payments.TransferRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inbox idempotente e replay de eventos de pagamento (RF-07).
 *
 * <p>A inserção em {@code inbox_events} usa {@code ON CONFLICT (event_id) DO
 * NOTHING} e acontece na mesma transação do efeito financeiro: ou o evento e a
 * liquidação são gravados juntos, ou nada é gravado e o Kafka reentrega. O
 * replay reexecuta o efeito de forma idempotente e registra a auditoria em
 * {@code inbox_replays}.
 */
@Service
public class InboxService {

  private final InboxEventRepository inbox;

  private final InboxReplayRepository replays;

  private final PaymentEventHandler handler;

  private final TransferRepository transfers;

  private final AccountRepository accounts;

  private final ObjectMapper objectMapper;

  public InboxService(
      InboxEventRepository inbox,
      InboxReplayRepository replays,
      PaymentEventHandler handler,
      TransferRepository transfers,
      AccountRepository accounts,
      ObjectMapper objectMapper) {
    this.inbox = inbox;
    this.replays = replays;
    this.handler = handler;
    this.transfers = transfers;
    this.accounts = accounts;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public InboxResult handle(
      InboundPaymentEvent event, String topic, int partition, long offset) {
    Instant now = Instant.now();
    int inserted =
        inbox.insertIfAbsent(
            UUID.randomUUID(),
            event.eventId(),
            event.eventType(),
            event.eventVersion(),
            event.aggregateId(),
            topic,
            partition,
            offset,
            event.payload(),
            now);
    if (inserted == 0) {
      return InboxResult.duplicate(event.eventId());
    }
    String outcome = handler.process(event);
    InboxEvent stored = inbox.findByEventId(event.eventId()).orElseThrow(InboxService::notFound);
    stored.markProcessed(now);
    return InboxResult.processed(event.eventId(), outcome);
  }

  /**
   * Reprocessa um evento já recebido, exigindo que o chamador seja dono do
   * pagamento. O efeito financeiro é idempotente e o replay fica auditado.
   */
  @Transactional
  public ReplayResult replay(UUID eventId, UUID ownerId) {
    InboxEvent stored = inbox.findByEventId(eventId).orElseThrow(InboxService::notFound);
    assertOwner(stored.getAggregateId(), ownerId);

    InboundPaymentEvent event = InboundPaymentEvent.fromJson(stored.getPayload(), objectMapper);
    String outcome = handler.process(event);
    Instant now = Instant.now();
    stored.markProcessed(now);
    InboxReplay replay =
        replays.save(
            new InboxReplay(UUID.randomUUID(), stored.getId(), ownerId, outcome, now));
    return new ReplayResult(stored.getEventId(), outcome, replay.getId(), now);
  }

  private void assertOwner(UUID paymentId, UUID ownerId) {
    Transfer transfer = transfers.findById(paymentId).orElseThrow(InboxService::notFound);
    Account source =
        accounts.findById(transfer.getSourceAccountId()).orElseThrow(InboxService::notFound);
    if (!source.getOwnerId().equals(ownerId)) {
      throw notFound();
    }
  }

  private static ApiException notFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "inbox_event_not_found", "Evento da inbox não encontrado.");
  }
}
