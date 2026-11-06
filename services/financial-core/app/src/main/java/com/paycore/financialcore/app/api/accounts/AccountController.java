package com.paycore.financialcore.app.api.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de conta do contrato OpenAPI (RF-01, RF-02). O proprietário vem
 * sempre do token autenticado — nunca do corpo ou da URL (RNF-06).
 */
@RestController
@RequestMapping("/accounts")
@Validated
public class AccountController {

  private final AccountApiService accounts;

  public AccountController(AccountApiService accounts) {
    this.accounts = accounts;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponse create(
      Principal principal, @Valid @RequestBody CreateAccountRequest request) {
    return accounts.create(ownerId(principal), request);
  }

  @GetMapping("/{accountId}")
  public AccountResponse get(
      Principal principal, @PathVariable("accountId") UUID accountId) {
    return accounts.get(accountId, ownerId(principal));
  }

  @GetMapping("/{accountId}/postings")
  public PostingPageResponse postings(
      Principal principal,
      @PathVariable("accountId") UUID accountId,
      @RequestParam(name = "cursor", required = false) String cursor,
      @RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(200) int limit) {
    return accounts.postings(accountId, ownerId(principal), cursor, limit);
  }

  private static UUID ownerId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}
