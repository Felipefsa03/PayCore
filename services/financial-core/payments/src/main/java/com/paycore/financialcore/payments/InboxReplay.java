package com.paycore.financialcore.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Auditoria de um replay de evento da inbox: quem pediu, quando e o resultado. */
@Entity
@Table(name = "inbox_replays")
public class InboxReplay {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "inbox_event_id", nullable = false, updatable = false)
  private UUID inboxEventId;

  @Column(name = "replayed_by", nullable = false, updatable = false)
  private UUID replayedBy;

  @Column(nullable = false, updatable = false, length = 40)
  private String outcome;

  @Column(name = "replayed_at", nullable = false, updatable = false)
  private Instant replayedAt;

  protected InboxReplay() {
    // required by JPA
  }

  public InboxReplay(
      UUID id, UUID inboxEventId, UUID replayedBy, String outcome, Instant replayedAt) {
    this.id = id;
    this.inboxEventId = inboxEventId;
    this.replayedBy = replayedBy;
    this.outcome = outcome;
    this.replayedAt = replayedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInboxEventId() {
    return inboxEventId;
  }

  public UUID getReplayedBy() {
    return replayedBy;
  }

  public String getOutcome() {
    return outcome;
  }

  public Instant getReplayedAt() {
    return replayedAt;
  }
}
