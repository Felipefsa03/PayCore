package com.paycore.financialcore.app.api.inbox;

import com.paycore.financialcore.app.inbox.InboxService;
import com.paycore.financialcore.app.inbox.ReplayResult;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replay auditável de um evento da inbox. O proprietário vem do token e o
 * serviço valida que o pagamento pertence a ele (RF-07).
 */
@RestController
@RequestMapping("/inbox")
public class InboxController {

  private final InboxService inbox;

  public InboxController(InboxService inbox) {
    this.inbox = inbox;
  }

  @PostMapping("/events/{eventId}/replay")
  public ReplayResponse replay(
      Principal principal, @PathVariable("eventId") UUID eventId) {
    ReplayResult result = inbox.replay(eventId, UUID.fromString(principal.getName()));
    return new ReplayResponse(
        result.eventId(), result.outcome(), result.replayId(), result.replayedAt());
  }
}
