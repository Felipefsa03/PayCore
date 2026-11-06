package com.paycore.financialcore.accounts;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de conta (RF-01, RF-02). A autorização por proprietário é
 * centralizada aqui para que qualquer adaptador de entrada (HTTP hoje,
 * mensageria no futuro) aplique a mesma regra (RNF-06).
 */
@Service
public class AccountService {

  private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

  private final AccountRepository accounts;

  public AccountService(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Transactional
  public Account create(UUID ownerId, String currency, Account.OverdraftPolicy overdraftPolicy) {
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    if (currency == null || !CURRENCY_PATTERN.matcher(currency).matches()) {
      throw new IllegalArgumentException("currency must be a 3-letter uppercase ISO 4217 code");
    }
    Objects.requireNonNull(overdraftPolicy, "overdraftPolicy must not be null");
    Account account = new Account(UUID.randomUUID(), ownerId, currency, overdraftPolicy);
    return accounts.save(account);
  }

  /**
   * Recupera a conta somente se ela pertencer ao proprietário autenticado.
   *
   * @throws AccountNotFoundException quando a conta não existe
   * @throws AccountAccessDeniedException quando a conta pertence a outro proprietário
   */
  @Transactional(readOnly = true)
  public Account requireOwned(UUID accountId, UUID ownerId) {
    Objects.requireNonNull(accountId, "accountId must not be null");
    Objects.requireNonNull(ownerId, "ownerId must not be null");
    Account account =
        accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    if (!account.getOwnerId().equals(ownerId)) {
      throw new AccountAccessDeniedException(accountId);
    }
    return account;
  }
}
