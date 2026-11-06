package com.paycore.financialcore.ledger;

import java.util.UUID;

/** Lançado quando um journal entry não soma zero por moeda (I-01). */
public class UnbalancedJournalEntryException extends IllegalStateException {

  private final UUID journalEntryId;
  private final long netAmount;

  public UnbalancedJournalEntryException(UUID journalEntryId, long netAmount) {
    super("journal entry " + journalEntryId + " is not balanced: net=" + netAmount);
    this.journalEntryId = journalEntryId;
    this.netAmount = netAmount;
  }

  public UUID getJournalEntryId() {
    return journalEntryId;
  }

  public long getNetAmount() {
    return netAmount;
  }
}
