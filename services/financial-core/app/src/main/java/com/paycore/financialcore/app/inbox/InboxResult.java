package com.paycore.financialcore.app.inbox;

import java.util.UUID;

/** Resultado do processamento de uma entrega: processado ou duplicado. */
public record InboxResult(UUID eventId, String outcome, boolean duplicate) {

  public static InboxResult duplicate(UUID eventId) {
    return new InboxResult(eventId, "DUPLICATE", true);
  }

  public static InboxResult processed(UUID eventId, String outcome) {
    return new InboxResult(eventId, outcome, false);
  }
}
