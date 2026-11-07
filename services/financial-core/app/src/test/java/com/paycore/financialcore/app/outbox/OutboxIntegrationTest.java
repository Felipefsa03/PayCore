package com.paycore.financialcore.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountService;
import com.paycore.financialcore.app.support.AbstractPostgresIntegrationTest;
import com.paycore.financialcore.app.transfers.TransferService;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.payments.OutboxEvent;
import com.paycore.financialcore.payments.OutboxEventRepository;
import com.paycore.financialcore.payments.Transfer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
    properties = {
      "paycore.outbox.scheduled=false",
      "paycore.outbox.batch-size=100",
      "paycore.outbox.retry-base-ms=1",
      "paycore.outbox.retry-max-ms=10",
      "paycore.outbox.max-attempts=5"
    })
@Import(OutboxIntegrationTest.PublisherConfig.class)
class OutboxIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AccountService accounts;
  @Autowired private LedgerService ledger;
  @Autowired private TransferService transfers;
  @Autowired private OutboxEventRepository outbox;
  @Autowired private OutboxDispatcher dispatcher;
  @Autowired private ControllablePublisher publisher;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void resetBrokerAndOutbox() {
    outbox.deleteAllInBatch();
    publisher.reset();
  }

  @Test
  void writesOutboxEventInTheSameTransactionAsTheTransfer() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1_000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    UUID correlationId = UUID.randomUUID();

    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            250L,
            "BRL",
            "key-" + UUID.randomUUID(),
            correlationId);

    List<OutboxEvent> events = outbox.findByAggregateTypeAndAggregateId("transfer", transfer.getId());
    assertThat(events).hasSize(1);
    OutboxEvent event = events.get(0);
    assertThat(event.getEventType()).isEqualTo("transfer.completed");
    assertThat(event.getEventVersion()).isEqualTo(1);
    assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    assertThat(event.getAttempts()).isZero();
    assertThat(event.getCorrelationId()).isEqualTo(correlationId);
    JsonNode payload = readPayload(event);
    assertThat(payload.path("transferId").asText()).isEqualTo(transfer.getId().toString());
    assertThat(payload.path("state").asText()).isEqualTo("COMPLETED");
    assertThat(payload.path("amount").asLong()).isEqualTo(250L);
    assertThat(payload.path("journalEntryId").asText())
        .isEqualTo(transfer.getJournalEntryId().toString());
  }

  @Test
  void writesRejectedEventForBusinessRejection() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 50L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);

    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            100L,
            "BRL",
            "key-" + UUID.randomUUID(),
            UUID.randomUUID());

    List<OutboxEvent> events = outbox.findByAggregateTypeAndAggregateId("transfer", transfer.getId());
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getEventType()).isEqualTo("transfer.rejected");
    assertThat(readPayload(events.get(0)).path("failureReason").asText())
        .isEqualTo("insufficient_funds");
  }

  @Test
  void rollsBackOutboxEventWhenThePaymentTransactionFails() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1_000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    UUID correlationId = UUID.randomUUID();
    TransactionTemplate template = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                template.executeWithoutResult(
                    status -> {
                      transfers.transfer(
                          owner,
                          source.getId(),
                          destination.getId(),
                          100L,
                          "BRL",
                          "rollback-" + UUID.randomUUID(),
                          correlationId);
                      throw new IllegalStateException("falha depois da confirmação");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(outbox.findByCorrelationId(correlationId)).isEmpty();
  }

  @Test
  void retriesWithBackoffWhenBrokerIsUnavailable() throws InterruptedException {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1_000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            100L,
            "BRL",
            "key-" + UUID.randomUUID(),
            UUID.randomUUID());
    OutboxEvent event =
        outbox.findByAggregateTypeAndAggregateId("transfer", transfer.getId()).get(0);

    publisher.fail();
    assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

    OutboxEvent afterFailure = outbox.findById(event.getId()).orElseThrow();
    assertThat(afterFailure.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
    assertThat(afterFailure.getAttempts()).isEqualTo(1);
    assertThat(afterFailure.getLastError()).contains("broker unavailable");

    publisher.recover();
    Thread.sleep(20L);
    assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

    OutboxEvent afterRecovery = outbox.findById(event.getId()).orElseThrow();
    assertThat(afterRecovery.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
    assertThat(afterRecovery.getPublishedAt()).isNotNull();
    assertThat(publisher.published).containsExactly(event.getId());
  }

  @Test
  void givesUpAfterTheMaximumNumberOfAttempts() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1_000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            100L,
            "BRL",
            "key-" + UUID.randomUUID(),
            UUID.randomUUID());
    OutboxEvent event =
        outbox.findByAggregateTypeAndAggregateId("transfer", transfer.getId()).get(0);

    publisher.fail();
    for (int attempt = 0; attempt < 5; attempt++) {
      dispatcher.dispatchOnce();
      await(25L);
    }

    OutboxEvent failed = outbox.findById(event.getId()).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
    assertThat(failed.getAttempts()).isEqualTo(5);
  }

  @Test
  void skipsRowsLockedByAnotherPublisher() throws Exception {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1_000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            100L,
            "BRL",
            "key-" + UUID.randomUUID(),
            UUID.randomUUID());
    OutboxEvent event =
        outbox.findByAggregateTypeAndAggregateId("transfer", transfer.getId()).get(0);

    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> holder =
          pool.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .executeWithoutResult(
                          status -> {
                            outbox.claimPending(100);
                            locked.countDown();
                            await(release);
                          }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

      assertThat(dispatcher.dispatchOnce()).isZero();
      assertThat(publisher.published).isEmpty();

      release.countDown();
      holder.get(5, TimeUnit.SECONDS);

      assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
      assertThat(publisher.published).containsExactly(event.getId());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  private JsonNode readPayload(OutboxEvent event) {
    try {
      return objectMapper.readTree(event.getPayload());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private Account fundedAccount(UUID owner, long amount) {
    Account account = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account system = accounts.create(owner, "BRL", Account.OverdraftPolicy.ALLOWED);
    ledger.record(UUID.randomUUID(), system.getId(), account.getId(), amount, "BRL");
    return account;
  }

  private static void await(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class PublisherConfig {

    @Bean
    @Primary
    ControllablePublisher controllablePublisher() {
      return new ControllablePublisher();
    }
  }

  static final class ControllablePublisher implements DomainEventPublisher {

    private final AtomicInteger attempts = new AtomicInteger();
    private final List<UUID> published = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean failing;

    @Override
    public void publish(OutboxEvent event) {
      attempts.incrementAndGet();
      if (failing) {
        throw new IllegalStateException("broker unavailable");
      }
      published.add(event.getId());
    }

    void fail() {
      failing = true;
    }

    void recover() {
      failing = false;
    }

    void reset() {
      failing = false;
      attempts.set(0);
      published.clear();
    }
  }
}
