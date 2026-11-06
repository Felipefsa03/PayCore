package com.paycore.financialcore.app.transfers;

import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountRepository;
import com.paycore.financialcore.accounts.AccountAccessDeniedException;
import com.paycore.financialcore.ledger.JournalEntry;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.payments.Transfer;
import com.paycore.financialcore.payments.TransferRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

  private final AccountRepository accounts;
  private final TransferRepository transfers;
  private final LedgerService ledger;

  public TransferService(
      AccountRepository accounts, TransferRepository transfers, LedgerService ledger) {
    this.accounts = accounts;
    this.transfers = transfers;
    this.ledger = ledger;
  }

  @Transactional
  public Transfer transfer(
      UUID ownerId,
      UUID sourceAccountId,
      UUID destinationAccountId,
      long amount,
      String currency,
      String idempotencyKey,
      UUID correlationId) {
    if (ownerId == null || sourceAccountId == null || destinationAccountId == null) {
      throw new IllegalArgumentException("owner and account ids must not be null");
    }
    if (sourceAccountId.equals(destinationAccountId)) {
      throw new IllegalArgumentException("source and destination accounts must differ");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
    if (currency == null || currency.length() != 3) {
      throw new IllegalArgumentException("currency must be a 3-letter code");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey must not be blank");
    }
    if (correlationId == null) {
      throw new IllegalArgumentException("correlationId must not be null");
    }

    Transfer existing = transfers.findByIdempotencyKey(idempotencyKey).orElse(null);
    if (existing != null) {
      return existing;
    }

    UUID first = sourceAccountId.compareTo(destinationAccountId) < 0 ? sourceAccountId : destinationAccountId;
    UUID second = sourceAccountId.compareTo(destinationAccountId) < 0 ? destinationAccountId : sourceAccountId;

    Account firstAccount =
        accounts.findByIdForUpdate(first).orElseThrow(() -> new IllegalArgumentException("account not found: " + first));
    Account secondAccount =
        accounts.findByIdForUpdate(second).orElseThrow(() -> new IllegalArgumentException("account not found: " + second));

    Account source = first.equals(sourceAccountId) ? firstAccount : secondAccount;
    Account destination = first.equals(sourceAccountId) ? secondAccount : firstAccount;

    if (!source.getOwnerId().equals(ownerId)) {
      throw new AccountAccessDeniedException(sourceAccountId);
    }
    if (!source.getCurrency().equals(currency) || !destination.getCurrency().equals(currency)) {
      throw new IllegalArgumentException("account currency must match transfer currency");
    }

    Transfer transfer =
        new Transfer(
            UUID.randomUUID(),
            sourceAccountId,
            destinationAccountId,
            amount,
            currency,
            idempotencyKey,
            correlationId);

    long sourceBalance = ledger.balanceOf(source.getId());
    if (source.getOverdraftPolicy() == Account.OverdraftPolicy.FORBIDDEN && sourceBalance < amount) {
      transfer.reject("insufficient_funds");
      return transfers.save(transfer);
    }

    transfer.markValidating();
    transfer.markProcessing();

    JournalEntry journal = ledger.record(transfer.getId(), source.getId(), destination.getId(), amount, currency);
    transfer.complete(journal.getId());
    return transfers.save(transfer);
  }
}
