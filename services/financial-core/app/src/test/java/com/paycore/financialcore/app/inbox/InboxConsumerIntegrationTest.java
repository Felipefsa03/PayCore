package com.paycore.financialcore.app.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountService;
import com.paycore.financialcore.app.outbox.kafka.TopicNames;
import com.paycore.financialcore.app.support.AbstractPostgresIntegrationTest;
import com.paycore.financialcore.app.transfers.TransferService;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.payments.InboxEvent;
import com.paycore.financialcore.payments.InboxEventRepository;
import com.paycore.financialcore.payments.InboxReplayRepository;
import com.paycore.financialcore.payments.OutboxEvent;
import com.paycore.financialcore.payments.OutboxEventRepository;
import com.paycore.financialcore.payments.SettlementEntryRepository;
import com.paycore.financialcore.payments.Transfer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@EmbeddedKafka(
    partitions = 3,
    topics = {TopicNames.PAYMENT_COMPLETED_V1, TopicNames.PAYMENT_REJECTED_V1})
@TestPropertySource(
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.kafka.consumer.auto-offset-reset=earliest",
      "paycore.inbox.consumer-enabled=true",
      "paycore.inbox.consumer-group=inbox-it",
      "paycore.outbox.scheduled=false"
    })
class InboxConsumerIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AccountService accounts;
  @Autowired private LedgerService ledger;
  @Autowired private TransferService transfers;
  @Autowired private OutboxEventRepository outbox;
  @Autowired private InboxEventRepository inbox;
  @Autowired private SettlementEntryRepository settlements;
  @Autowired private InboxReplayRepository replays;
  @Autowired private KafkaTemplate<String, String> kafka;

  @BeforeEach
  void cleanInbox() {
    replays.deleteAllInBatch();
    settlements.deleteAllInBatch();
    inbox.deleteAllInBatch();
    outbox.deleteAllInBatch();
  }

  @Test
  void duplicateDeliveriesCreateOnlyOneSettlement() throws Exception {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1_000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            400L,
            "BRL",
            "key-" + UUID.randomUUID(),
            UUID.randomUUID());
    OutboxEvent outboxEvent =
        outbox.findByAggregateTypeAndAggregateId("transfer", transfer.getId()).get(0);
    String payload = envelope(outboxEvent, transfer);

    kafka.send(TopicNames.PAYMENT_COMPLETED_V1, transfer.getId().toString(), payload)
        .get(10, TimeUnit.SECONDS);
    awaitProcessed(outboxEvent.getId());

    kafka.send(TopicNames.PAYMENT_COMPLETED_V1, transfer.getId().toString(), payload)
        .get(10, TimeUnit.SECONDS);
    Thread.sleep(1_500L);

    assertThat(inbox.count()).isEqualTo(1);
    assertThat(settlements.count()).isEqualTo(1);
    InboxEvent stored = inbox.findByEventId(outboxEvent.getId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(InboxEvent.Status.PROCESSED);
  }

  private void awaitProcessed(UUID eventId) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Optional<InboxEvent> event = inbox.findByEventId(eventId);
      if (event.isPresent() && event.get().getStatus() == InboxEvent.Status.PROCESSED) {
        return;
      }
      Thread.sleep(100L);
    }
    fail("event " + eventId + " was not processed within 30s");
  }

  private String envelope(OutboxEvent outboxEvent, Transfer transfer) throws Exception {
    ObjectMapper mapper = objectMapper;
    ObjectNode root = mapper.createObjectNode();
    root.put("event_id", outboxEvent.getId().toString());
    root.put("event_type", "payment.completed");
    root.put("event_version", 1);
    root.put("occurred_at", outboxEvent.getCreatedAt().toString());
    root.put("correlation_id", outboxEvent.getCorrelationId().toString());
    root.putObject("aggregate").put("type", "payment").put("id", transfer.getId().toString());
    root.set("data", mapper.readTree(outboxEvent.getPayload()));
    return mapper.writeValueAsString(root);
  }

  private Account fundedAccount(UUID owner, long amount) {
    Account account = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account system = accounts.create(owner, "BRL", Account.OverdraftPolicy.ALLOWED);
    ledger.record(UUID.randomUUID(), system.getId(), account.getId(), amount, "BRL");
    return account;
  }
}
