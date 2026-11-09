package com.paycore.financialcore.app.inbox;

import java.time.Instant;
import java.util.UUID;

/** Resultado auditável de um replay. */
public record ReplayResult(UUID eventId, String outcome, UUID replayId, Instant replayedAt) {
}
