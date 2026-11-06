package com.paycore.financialcore.app.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountService;
import com.paycore.financialcore.app.support.AbstractPostgresIntegrationTest;
import com.paycore.financialcore.ledger.JournalEntry;
import com.paycore.financialcore.ledger.JournalEntryRepository;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.ledger.Posting;
import com.paycore.financialcore.ledger.PostingRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class LedgerIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AccountService accounts;
  @Autowired private LedgerService ledger;
  @Autowired private JournalEntryRepository journals;
  @Autowired private PostingRepository postings;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void recordsBalancedEntryAndUpdateBalance() {
    UUID owner = UUID.randomUUID();
    Account source = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    UUID transferId = UUID.randomUUID();

    JournalEntry entry = ledger.record(transferId, source.getId(), destination.getId(), 1000L, "BRL");

    assertThat(entry.getPostings()).hasSize(2);
    assertThat(postings.balanceOf(source.getId())).isEqualTo(-1000L);
    assertThat(postings.balanceOf(destination.getId())).isEqualTo(1000L);
    assertThat(ledger.findByTransferId(transferId)).isPresent();
  }

  @Test
  void rejectsDuplicateTransferId() {
    UUID owner = UUID.randomUUID();
    Account source = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    UUID transferId = UUID.randomUUID();

    ledger.record(transferId, source.getId(), destination.getId(), 500L, "BRL");

    assertThatThrownBy(() -> ledger.record(transferId, source.getId(), destination.getId(), 500L, "BRL"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reversalPreservesOriginalAndCompensates() {
    UUID owner = UUID.randomUUID();
    Account source = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    UUID original = UUID.randomUUID();
    UUID reversal = UUID.randomUUID();

    ledger.record(original, source.getId(), destination.getId(), 1000L, "BRL");
    long sourceBefore = postings.balanceOf(source.getId());
    long destinationBefore = postings.balanceOf(destination.getId());

    ledger.reverse(original, reversal, source.getId(), destination.getId(), 1000L, "BRL");

    assertThat(postings.balanceOf(source.getId())).isEqualTo(sourceBefore + 1000L);
    assertThat(postings.balanceOf(destination.getId())).isEqualTo(destinationBefore - 1000L);
    assertThat(ledger.findByTransferId(original)).isPresent();
    assertThat(ledger.findByTransferId(reversal)).isPresent();
  }

  @Test
  void rejectsUnbalancedJournalAtDatabase() {
    UUID owner = UUID.randomUUID();
    Account source = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    UUID journalId = UUID.randomUUID();
    UUID transferId = UUID.randomUUID();

    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    assertThatThrownBy(
            () ->
                tx.execute(
                    status -> {
                      jdbc.update(
                          "INSERT INTO journal_entries (id, transfer_id, currency, created_at) VALUES (?,?,?,now())",
                          journalId, transferId, "BRL");
                      jdbc.update(
                          "INSERT INTO postings (id, journal_entry_id, account_id, direction, amount, currency, created_at) "
                              + "VALUES (?,?,?,?,?,?,now())",
                          UUID.randomUUID(), journalId, source.getId(), "DEBIT", 1000L, "BRL");
                      return null;
                    }))
        .isInstanceOf(Exception.class);
  }

  @Test
  void rejectsMutationOfPostedEntry() {
    UUID owner = UUID.randomUUID();
    Account source = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    UUID transferId = UUID.randomUUID();

    ledger.record(transferId, source.getId(), destination.getId(), 1000L, "BRL");
    UUID postingId = postings.findAll().stream().findFirst().orElseThrow().getId();

    assertThatThrownBy(
            () -> jdbc.update("UPDATE postings SET amount = amount + 1 WHERE id = ?", postingId))
        .isInstanceOf(Exception.class);
  }
}
