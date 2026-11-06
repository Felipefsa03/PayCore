package com.paycore.financialcore.accounts;

import java.util.UUID;

/**
 * Conta inexistente. Mapeada para {@code 404 account_not_found} na API
 * (RF-01, RF-02).
 */
public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException(UUID accountId) {
    super("Conta não encontrada: " + accountId);
  }
}
