package com.paycore.financialcore.app.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountService;
import com.paycore.financialcore.app.api.ApiException;
import com.paycore.financialcore.app.outbox.kafka.TopicNames;
import com.paycore.financialcore.app.support.AbstractPostgresIntegrationTest;
import com.paycore.financialcore.app.transfers.TransferService;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.payments.InboxEvent;
import com.paycore.financialcore.payments.InboxEventRepository;
import com.paycore.financialcore.payments.InboxReplay;
import com.paycore.financialcore.payments.InboxReplayRepository;
import com.paycore.financialcore.payments.OutboxEvent;
import com.paycore.financialcore.payments.OutboxEventRepository;
import com.paycore.financialcore.payments.SettlementEntryRepository;
import com.paycore.financialcore.payments.Transfer;
import com.paycore.financialcore.shared.TransferState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "paycore.outbox.scheduled=false",
      "paycore.inbox.consumer-enabled=false"
    })
class InboxIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AccountService accounts;
  @Autowired private LedgerService ledger;
  @Autowired private TransferService transfers;
  @Autowired private OutboxEventRepository outbox;
  @Autowired private InboxEventRepository inbox;
  @Autowired private SettlementEntryRepository settlements;
  @Autowired private InboxReplayRepository replays;
  @Autowired private InboxService inboxService;

  @BeforeEach
  void cleanInbox() {
    replays.deleteAllInBatch();
    settlements.deleteAllInBatch();
    inbox.deleteAllInBatch();
    outbox.deleteAllInBatch();
  }

  @Test
  void processesDeliveryOnceAndIgnoresTheDuplicate() throws Exception {
    UUID owner = UUID.randomUUID();
    Sample sample = completedSample(owner);

    InboxResult first =
        inboxService.handle(sample.event(), TopicNames.PAYMENT_COMPLETED_V1, 0, 10L);
    InboxResult duplicate =
        inboxService.handle(sample.event(), TopicNames.PAYMENT_COMPLETED_V1, 0, 11L);

    assertThat(first.duplicate()).isFalse();
    assertThat(first.outcome()).isEqualTo(PaymentEventHandler.SETTLED);
    assertThat(duplicate.duplicate()).isTrue();
    assertThat(inbox.count()).isEqualTo(1);
    assertThat(settlements.count()).isEqualTo(1);

    InboxEvent stored = inbox.findByEventId(sample.event().eventId()).orElseThrow();
    assertThat(stored.getStatus()).isEqualTo(InboxEvent.Status.PROCESSED);
    assertThat(stored.getProcessedAt()).isNotNull();
  }

  @Test
  void replayIsAuditedAndDoesNotDuplicateTheFinancialEffect() throws Exception {
    UUID owner = UUID.randomUUID();
    Sample sample = completedSample(owner);
    inboxService.handle(sample.event(), TopicNames.PAYMENT_COMPLETED_V1, 0, 10L);

    ReplayResult replay = inboxService.replay(sample.event().eventId(), owner);

    assertThat(replay.outcome()).isEqualTo(PaymentEventHandler.ALREADY_SETTLED);
    assertThat(settlements.count()).isEqualTo(1);

    InboxEvent stored = inbox.findByEventId(sample.event().eventId()).orElseThrow();
    List<InboxReplay> audit = replays.findByInboxEventIdOrderByReplayedAtDesc(stored.getId());
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0).getReplayedBy()).isEqualTo(owner);
    assertThat(audit.get(0).getOutcome()).isEqualTo(PaymentEventHandler.ALREADY_SETTLED);
  }

  @Test
  void replayIsDeniedForAnotherOwner() throws Exception {
    UUID owner = UUID.randomUUID();
    Sample sample = completedSample(owner);
    inboxService.handle(sample.event(), TopicNames.PAYMENT_COMPLETED_V1, 0, 10L);

    assertThatThrownBy(() -> inboxService.replay(sample.event().eventId(), UUID.randomUUID()))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);

    assertThat(replays.count()).isZero();
    assertThat(settlements.count()).isEqualTo(1);
  }

  @Test
  void replayEndpointReturnsTheAuditTrail() throws Exception {
    UUID owner = UUID.randomUUID();
    Sample sample = completedSample(owner);
    inboxService.handle(sample.event(), TopicNames.PAYMENT_COMPLETED_V1, 0, 10L);

    mvc.perform(
            post("/inbox/events/{eventId}/replay", sample.event().eventId())
                .header("Authorization", bearer(tokenFor(owner))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventId").value(sample.event().eventId().toString()))
        .andExpect(jsonPath("$.outcome").value(PaymentEventHandler.ALREADY_SETTLED))
        .andExpect(jsonPath("$.replayId").isNotEmpty());

    assertThat(replays.count()).isEqualTo(1);
  }

  @Test
  void rejectedPaymentHasNoFinancialEffect() throws Exception {
    UUID owner = UUID.randomUUID();
    Sample sample = rejectedSample(owner);

    InboxResult result =
        inboxService.handle(sample.event(), TopicNames.PAYMENT_REJECTED_V1, 1, 5L);

    assertThat(result.duplicate()).isFalse();
    assertThat(result.outcome()).isEqualTo(PaymentEventHandler.NO_FINANCIAL_EFFECT);
    assertThat(inbox.count()).isEqualTo(1);
    assertThat(settlements.count()).isZero();
  }

  private Sample completedSample(UUID owner) throws Exception {
    Account source = fundedAccount(owner, 1_000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    return sample(owner, source, destination, 400L);
  }

  private Sample rejectedSample(UUID owner) throws Exception {
    Account source = fundedAccount(owner, 100L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    return sample(owner, source, destination, 200L);
  }

  private Sample sample(UUID owner, Account source, Account destination, long amount)
      throws Exception {
    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            amount,
            "BRL",
            "key-" + UUID.randomUUID(),
            UUID.randomUUID());
    OutboxEvent outboxEvent =
        outbox.findByAggregateTypeAndAggregateId("transfer", transfer.getId()).get(0);
    String eventType =
        transfer.getState() == TransferState.COMPLETED
            ? "payment.completed"
            : "payment.rejected";
    return new Sample(transfer, envelope(outboxEvent, transfer, eventType));
  }

  private InboundPaymentEvent envelope(OutboxEvent outboxEvent, Transfer transfer, String eventType)
      throws Exception {
    ObjectMapper mapper = objectMapper;
    ObjectNode root = mapper.createObjectNode();
    root.put("event_id", outboxEvent.getId().toString());
    root.put("event_type", eventType);
    root.put("event_version", 1);
    root.put("occurred_at", outboxEvent.getCreatedAt().toString());
    root.put("correlation_id", outboxEvent.getCorrelationId().toString());
    root.putObject("aggregate").put("type", "payment").put("id", transfer.getId().toString());
    root.set("data", mapper.readTree(outboxEvent.getPayload()));
    return InboundPaymentEvent.fromJson(mapper.writeValueAsString(root), mapper);
  }

  private Account fundedAccount(UUID owner, long amount) {
    Account account = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account system = accounts.create(owner, "BRL", Account.OverdraftPolicy.ALLOWED);
    ledger.record(UUID.randomUUID(), system.getId(), account.getId(), amount, "BRL");
    return account;
  }

  private record Sample(Transfer transfer, InboundPaymentEvent event) {
    // test fixture
  }
}
