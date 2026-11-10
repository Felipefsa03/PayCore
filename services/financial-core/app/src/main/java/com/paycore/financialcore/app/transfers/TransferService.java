package com.paycore.financialcore.app.transfers;

import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountRepository;
import com.paycore.financialcore.accounts.AccountAccessDeniedException;
import com.paycore.financialcore.ledger.JournalEntry;
import com.paycore.financialcore.app.idempotency.IdempotencyRecord;
import com.paycore.financialcore.app.idempotency.IdempotencyService;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.payments.OutboxEventService;
import com.paycore.financialcore.payments.Transfer;
import com.paycore.financialcore.payments.TransferRepository;
import com.paycore.financialcore.app.risk.RiskClient;
import com.paycore.financialcore.app.risk.RiskEvaluation;
import com.paycore.financialcore.app.risk.RiskUnavailableException;
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
  private final OutboxEventService outbox;
  private final RiskClient risk;

  public TransferService(
      AccountRepository accounts,
      TransferRepository transfers,
      LedgerService ledger,
      IdempotencyService idempotency,
      OutboxEventService outbox,
      RiskClient risk) {
    this.accounts = accounts;
    this.transfers = transfers;
    this.ledger = ledger;
    this.idempotency = idempotency;
    this.outbox = outbox;
    this.risk = risk;
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
    return submit(ownerId, sourceAccountId, destinationAccountId, amount, currency, idempotencyKey, correlationId).transfer();
  }

  public record Submission(Transfer transfer, boolean replay) {}

  @Transactional
  public Submission submit(UUID ownerId, UUID sourceAccountId, UUID destinationAccountId,
      long amount, String currency, String idempotencyKey, UUID correlationId) {
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
      return new Submission(transfers.findById(record.getTransferId()).orElseThrow(), true);
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

    UUID transferId = UUID.randomUUID();
    Transfer transfer = new Transfer(
        transferId, sourceAccountId, destinationAccountId, amount, currency, idempotencyKey, correlationId);
    String rejectionReason;
    try {
      Instant now = Instant.now();
      long recent = risk.isEnabled()
          ? transfers.countBySourceAccountIdAndCreatedAtAfter(sourceAccountId, now.minus(java.time.Duration.ofDays(1)))
          : 0;
      RiskEvaluation evaluation = risk.evaluate(transferId, sourceAccountId, amount, currency,
          java.time.Duration.between(source.getCreatedAt(), now).toDays(), recent);
      rejectionReason = switch (evaluation.decision()) {
        case "APPROVE" -> null;
        case "REJECT" -> "risk_rejected";
        case "REVIEW" -> "risk_review_required";
        default -> "risk_unavailable";
      };
    } catch (RiskUnavailableException exception) {
      rejectionReason = "risk_unavailable";
    }

    try {
      long sourceBalance = ledger.balanceOf(source.getId());
      if (rejectionReason == null && source.getOverdraftPolicy() == Account.OverdraftPolicy.FORBIDDEN && sourceBalance < amount) {
        rejectionReason = "insufficient_funds";
      }
      if (rejectionReason != null) {
        transfer.reject(rejectionReason);
        Transfer saved = transfers.saveAndFlush(transfer);
        outbox.append(
            "transfer",
            saved.getId(),
            "transfer.rejected",
            1,
            transferPayload(saved),
            saved.getCorrelationId());
        idempotency.complete(record, saved.getId(), snapshot(saved));
        return new Submission(saved, false);
      }

      transfer.markValidating();
      transfer.markProcessing();

      JournalEntry journal = ledger.record(transfer.getId(), source.getId(), destination.getId(), amount, currency);
      transfer.complete(journal.getId());
      Transfer saved = transfers.saveAndFlush(transfer);
      outbox.append(
          "transfer",
          saved.getId(),
          "transfer.completed",
          1,
          transferPayload(saved),
          saved.getCorrelationId());
      idempotency.complete(record, saved.getId(), snapshot(saved));
      return new Submission(saved, false);
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

  /**
   * Payload do evento de domínio. Os campos são UUIDs, números e enums
   * controlados, por isso a montagem textual é segura e dispensa serializador
   * no módulo de pagamentos.
   */
  private static String transferPayload(Transfer transfer) {
    StringBuilder payload = new StringBuilder();
    payload.append("{\"transferId\":\"").append(transfer.getId()).append('"');
    payload.append(",\"state\":\"").append(transfer.getState()).append('"');
    payload.append(",\"sourceAccountId\":\"").append(transfer.getSourceAccountId()).append('"');
    payload.append(",\"destinationAccountId\":\"").append(transfer.getDestinationAccountId()).append('"');
    payload.append(",\"amount\":").append(transfer.getAmount());
    payload.append(",\"currency\":\"").append(transfer.getCurrency()).append('"');
    payload.append(",\"correlationId\":\"").append(transfer.getCorrelationId()).append('"');
    if (transfer.getJournalEntryId() != null) {
      payload.append(",\"journalEntryId\":\"").append(transfer.getJournalEntryId()).append('"');
    }
    if (transfer.getFailureReason() != null) {
      payload.append(",\"failureReason\":\"").append(transfer.getFailureReason()).append('"');
    }
    payload.append('}');
    return payload.toString();
  }
}
