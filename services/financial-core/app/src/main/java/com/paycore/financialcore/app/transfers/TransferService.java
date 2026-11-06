package com.paycore.financialcore.app.transfers;

import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountRepository;
import com.paycore.financialcore.accounts.AccountAccessDeniedException;
import com.paycore.financialcore.ledger.JournalEntry;
import com.paycore.financialcore.app.idempotency.IdempotencyRecord;
import com.paycore.financialcore.app.idempotency.IdempotencyService;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.payments.Transfer;
import com.paycore.financialcore.payments.TransferRepository;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

  private final AccountRepository accounts;
  private final TransferRepository transfers;
  private final LedgerService ledger;
  private final IdempotencyService idempotency;

  public TransferService(
      AccountRepository accounts,
      TransferRepository transfers,
      LedgerService ledger,
      IdempotencyService idempotency) {
    this.accounts = accounts;
    this.transfers = transfers;
    this.ledger = ledger;
    this.idempotency = idempotency;
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

    String fingerprint = fingerprint(sourceAccountId, destinationAccountId, amount, currency);

    IdempotencyRecord record =
        idempotency.begin(
            ownerId,
            "transfer",
            idempotencyKey,
            fingerprint,
            Instant.now().plusSeconds(30L * 24 * 60 * 60));

    if (IdempotencyRecord.Status.COMPLETED.name().equals(record.getStatus())
        && record.getTransferId() != null) {
      return transfers.findById(record.getTransferId()).orElseThrow();
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

    try {
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
        Transfer saved = transfers.saveAndFlush(transfer);
        idempotency.complete(record, saved.getId(), snapshot(saved));
        return saved;
      }

      transfer.markValidating();
      transfer.markProcessing();

      JournalEntry journal = ledger.record(transfer.getId(), source.getId(), destination.getId(), amount, currency);
      transfer.complete(journal.getId());
      Transfer saved = transfers.saveAndFlush(transfer);
      idempotency.complete(record, saved.getId(), snapshot(saved));
      return saved;
    } catch (RuntimeException exception) {
      idempotency.release(record);
      throw exception;
    }
  }

  private static String fingerprint(UUID source, UUID destination, long amount, String currency) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash =
          digest.digest((source + "|" + destination + "|" + amount + "|" + currency).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte value : hash) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String snapshot(Transfer transfer) {
    return "{\"transferId\":\"" + transfer.getId() + "\",\"state\":\"" + transfer.getState() + "\"}";
  }
}
