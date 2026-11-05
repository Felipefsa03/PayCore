package com.paycore.financialcore.accounts;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a contas. A lógica de autorização por proprietário (RF-02, RNF-06)
 * é aplicada na camada de API, a definir no card dedicado de contas simuladas.
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

  List<Account> findByOwnerId(UUID ownerId);
}
