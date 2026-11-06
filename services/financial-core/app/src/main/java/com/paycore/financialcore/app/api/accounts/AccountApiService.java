package com.paycore.financialcore.app.api.accounts;

import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountService;
import com.paycore.financialcore.app.api.ApiException;
import com.paycore.financialcore.ledger.Posting;
import com.paycore.financialcore.ledger.PostingRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composição da API de contas: une as regras de conta (módulo {@code accounts})
 * à projeção de saldo e histórico (módulo {@code ledger}). É o único ponto que
 * conhece os dois módulos (RFC-001 §7, composição em {@code app}).
 */
@Service
public class AccountApiService {

  private final AccountService accounts;
  private final PostingRepository postings;

  public AccountApiService(AccountService accounts, PostingRepository postings) {
    this.accounts = accounts;
    this.postings = postings;
  }

  @Transactional
  public AccountResponse create(UUID ownerId, CreateAccountRequest request) {
    Account account =
        accounts.create(ownerId, request.currency(), overdraftPolicy(request.overdraftPolicy()));
    return toResponse(account);
  }

  @Transactional(readOnly = true)
  public AccountResponse get(UUID accountId, UUID ownerId) {
    return toResponse(accounts.requireOwned(accountId, ownerId));
  }

  @Transactional(readOnly = true)
  public PostingPageResponse postings(UUID accountId, UUID ownerId, String cursor, int limit) {
    accounts.requireOwned(accountId, ownerId);

    List<Posting> rows;
    if (cursor == null || cursor.isBlank()) {
      rows = postings.findFirstPage(accountId, PageRequest.of(0, limit + 1));
    } else {
      Posting anchor = anchor(accountId, cursor);
      rows =
          postings.findPageBefore(
              accountId, anchor.getCreatedAt(), anchor.getId(), PageRequest.of(0, limit + 1));
    }

    boolean hasMore = rows.size() > limit;
    List<Posting> page = hasMore ? rows.subList(0, limit) : rows;
    String nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;
    return new PostingPageResponse(page.stream().map(PostingResponse::from).toList(), nextCursor);
  }

  private Posting anchor(UUID accountId, String cursor) {
    UUID cursorId;
    try {
      cursorId = UUID.fromString(cursor);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "validation_error", "cursor inválido: use o nextCursor recebido.");
    }
    return postings
        .findById(cursorId)
        .filter(posting -> posting.getAccountId().equals(accountId))
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "validation_error",
                    "cursor inválido para esta conta."));
  }

  private Account.OverdraftPolicy overdraftPolicy(String value) {
    if (value == null) {
      return Account.OverdraftPolicy.FORBIDDEN;
    }
    try {
      return Account.OverdraftPolicy.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "validation_error",
          "overdraftPolicy inválido: use 'forbidden' ou 'allowed'.");
    }
  }

  private AccountResponse toResponse(Account account) {
    return new AccountResponse(
        account.getId(),
        account.getOwnerId(),
        account.getCurrency(),
        account.getOverdraftPolicy().name().toLowerCase(Locale.ROOT),
        account.getStatus().name().toLowerCase(Locale.ROOT),
        postings.balanceOf(account.getId()),
        account.getCreatedAt());
  }
}
