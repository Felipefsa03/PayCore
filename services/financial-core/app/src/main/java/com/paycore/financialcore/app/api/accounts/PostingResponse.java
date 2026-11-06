package com.paycore.financialcore.app.api.accounts;

import com.paycore.financialcore.ledger.Posting;
import java.time.Instant;
import java.util.UUID;

/** Lançamento exposto em {@code GET /accounts/{accountId}/postings}. */
public record PostingResponse(
    UUID id,
    UUID journalEntryId,
    UUID accountId,
    String direction,
    long amount,
    String currency,
    Instant createdAt) {

  public static PostingResponse from(Posting posting) {
    return new PostingResponse(
        posting.getId(),
        posting.getJournalEntry().getId(),
        posting.getAccountId(),
        posting.getDirection().name(),
        posting.getAmount(),
        posting.getCurrency(),
        posting.getCreatedAt());
  }
}
