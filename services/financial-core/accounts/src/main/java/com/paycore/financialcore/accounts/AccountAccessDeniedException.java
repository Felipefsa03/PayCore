package com.paycore.financialcore.accounts;

import java.util.UUID;

/**
 * Tentativa de acessar conta de outro proprietário. Mapeada para
 * {@code 403 forbidden} na API (RF-02, RNF-06).
 */
public class AccountAccessDeniedException extends RuntimeException {

  public AccountAccessDeniedException(UUID accountId) {
    super("A conta " + accountId + " não pertence ao proprietário autenticado.");
  }
}
