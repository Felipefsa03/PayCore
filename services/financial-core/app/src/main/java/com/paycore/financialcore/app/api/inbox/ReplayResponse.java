package com.paycore.financialcore.app.api.inbox;

import java.time.Instant;
import java.util.UUID;

/** Resposta do replay: resultado financeiro idempotente e trilha de auditoria. */
public record ReplayResponse(UUID eventId, String outcome, UUID replayId, Instant replayedAt) {
}
