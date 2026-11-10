package com.paycore.financialcore.app.transfers;

import com.paycore.financialcore.accounts.AccountRepository;
import com.paycore.financialcore.payments.Transfer;
import com.paycore.financialcore.payments.TransferRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.paycore.financialcore.app.api.ApiException;

@RestController
@RequestMapping("/transfers")
public class TransferController {
  private final TransferService service;
  private final TransferRepository transfers;
  private final AccountRepository accounts;

  public TransferController(TransferService service, TransferRepository transfers, AccountRepository accounts) {
    this.service = service;
    this.transfers = transfers;
    this.accounts = accounts;
  }

  public record Create(@NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
      @Positive long amount, @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {}

  public record View(UUID id, UUID sourceAccountId, UUID destinationAccountId, long amount,
      String currency, String state, String failureReason, UUID journalEntryId, UUID correlationId,
      Instant createdAt, Instant updatedAt) {
    static View of(Transfer t) {
      return new View(t.getId(), t.getSourceAccountId(), t.getDestinationAccountId(), t.getAmount(),
          t.getCurrency(), t.getState().name(), t.getFailureReason(), t.getJournalEntryId(),
          t.getCorrelationId(), t.getCreatedAt(), t.getUpdatedAt());
    }
  }

  @PostMapping
  public ResponseEntity<View> create(Principal principal, @Valid @RequestBody Create request,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String key,
      @RequestHeader(value = "X-Correlation-Id", required = false) UUID correlationId) {
    var result = service.submit(UUID.fromString(principal.getName()), request.sourceAccountId(),
        request.destinationAccountId(), request.amount(), request.currency(), key,
        correlationId == null ? UUID.randomUUID() : correlationId);
    return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
        .body(View.of(result.transfer()));
  }

  @GetMapping("/{id}")
  public View get(Principal principal, @PathVariable("id") UUID id) {
    UUID owner = UUID.fromString(principal.getName());
    return transfers.findById(id)
        .filter(t -> accounts.findById(t.getSourceAccountId()).filter(a -> owner.equals(a.getOwnerId())).isPresent())
        .map(View::of).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "transfer_not_found", "Transferência não encontrada."));
  }
}
