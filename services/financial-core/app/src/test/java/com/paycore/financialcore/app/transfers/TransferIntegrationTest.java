package com.paycore.financialcore.app.transfers;

import static org.assertj.core.api.Assertions.assertThat;

import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountService;
import com.paycore.financialcore.app.support.AbstractPostgresIntegrationTest;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.app.idempotency.IdempotencyKeyConflictException;
import com.paycore.financialcore.payments.Transfer;
import com.paycore.financialcore.payments.TransferRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TransferIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final UUID SYSTEM_ACCOUNT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000099");

  @Autowired private AccountService accounts;
  @Autowired private TransferService transfers;
  @Autowired private LedgerService ledger;
  @Autowired private TransferRepository transferRepository;

  private Account fundedAccount(UUID owner, long amount) {
    Account account = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account system = accounts.create(owner, "BRL", Account.OverdraftPolicy.ALLOWED);
    ledger.record(UUID.randomUUID(), system.getId(), account.getId(), amount, "BRL");
    return account;
  }

  @Test
  void completesTransferAndUpdatesBalances() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);

    Transfer transfer =
        transfers.transfer(
            owner, source.getId(), destination.getId(), 400L, "BRL", "key-" + UUID.randomUUID(), UUID.randomUUID());

    assertThat(transfer.getState()).isEqualTo(com.paycore.financialcore.shared.TransferState.COMPLETED);
    assertThat(transfer.getJournalEntryId()).isNotNull();
    assertThat(ledger.findByTransferId(transfer.getId())).isPresent();
    assertThat(ledger.balanceOf(source.getId())).isEqualTo(600L);
    assertThat(ledger.balanceOf(destination.getId())).isEqualTo(400L);
  }

  @Test
  void rejectsInsufficientFunds() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 100L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);

    Transfer transfer =
        transfers.transfer(
            owner, source.getId(), destination.getId(), 200L, "BRL", "key-" + UUID.randomUUID(), UUID.randomUUID());

    assertThat(transfer.getState()).isEqualTo(com.paycore.financialcore.shared.TransferState.REJECTED);
    assertThat(ledger.balanceOf(source.getId())).isEqualTo(100L);
    assertThat(ledger.balanceOf(destination.getId())).isEqualTo(0L);
  }

  @Test
  void concurrentTransfersNeverOverdraw() throws InterruptedException {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 50L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);

    int threads = 5;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
      final int idx = i;
      pool.submit(
          () -> {
            try {
              start.await();
              Transfer t =
                  transfers.transfer(
                      owner,
                      source.getId(),
                      destination.getId(),
                      10L,
                      "BRL",
                      "concurrent-key-" + idx,
                      UUID.randomUUID());
              if (t.getState() == com.paycore.financialcore.shared.TransferState.COMPLETED) {
                successes.incrementAndGet();
              } else {
                rejected.incrementAndGet();
              }
            } catch (Throwable e) {
              rejected.incrementAndGet();
              System.err.println("ERR: " + e);
            } finally {
              done.countDown();
            }
          });
    }
    start.countDown();
    boolean finished = done.await(60, TimeUnit.SECONDS);
    if (!finished) { System.err.println("TIMEOUT: only " + (successes.get()+rejected.get()) + " finished"); }
    pool.shutdown();

    long sourceBalance = ledger.balanceOf(source.getId());
    long destinationBalance = ledger.balanceOf(destination.getId());

    assertThat(sourceBalance).isGreaterThanOrEqualTo(0L);
    assertThat(sourceBalance + destinationBalance).isEqualTo(50L);
    assertThat(successes.get() + rejected.get()).isEqualTo(threads);
    assertThat(successes.get()).isLessThanOrEqualTo(5);
  }

  @Test
  void replaysSameTransferForSameKeyAndPayload() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    String key = "replay-" + UUID.randomUUID();

    Transfer first =
        transfers.transfer(owner, source.getId(), destination.getId(), 100L, "BRL", key, UUID.randomUUID());
    Transfer second =
        transfers.transfer(owner, source.getId(), destination.getId(), 100L, "BRL", key, UUID.randomUUID());

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(ledger.balanceOf(destination.getId())).isEqualTo(100L);
  }

  @Test
  void rejectsReusedKeyWithDifferentPayload() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    String key = "conflict-" + UUID.randomUUID();

    transfers.transfer(owner, source.getId(), destination.getId(), 100L, "BRL", key, UUID.randomUUID());

    org.junit.jupiter.api.Assertions.assertThrows(
        IdempotencyKeyConflictException.class,
        () ->
            transfers.transfer(
                owner, source.getId(), destination.getId(), 200L, "BRL", key, UUID.randomUUID()));
  }
}
