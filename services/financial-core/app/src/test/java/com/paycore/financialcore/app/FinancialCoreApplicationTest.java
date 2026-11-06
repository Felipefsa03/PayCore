package com.paycore.financialcore.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountRepository;
import com.paycore.financialcore.app.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prova de que o monólito modular sobe contra um Postgres real, aplica as
 * migrations Flyway, e que os módulos accounts/ledger/payments estão
 * corretamente ligados em um único contexto Spring (confirmação do card de
 * bootstrap: "docker compose up funciona, migrations aplicam e o CI fica
 * verde").
 *
 * <p>A asserção de migrations é intencionalmente independente de dados: outras
 * classes de integração compartilham o mesmo banco e podem já ter criado contas.
 */
class FinancialCoreApplicationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AccountRepository accountRepository;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void bootsAndAppliesFlywayMigrations() {
    Integer applied =
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
    assertThat(applied).isNotNull().isGreaterThanOrEqualTo(3);
  }

  @Test
  void persistsAndReadsAnAccountThroughTheRealSchema() {
    Account account =
        new Account(UUID.randomUUID(), UUID.randomUUID(), "BRL", Account.OverdraftPolicy.FORBIDDEN);
    accountRepository.save(account);

    Account found = accountRepository.findById(account.getId()).orElseThrow();

    assertThat(found.getCurrency()).isEqualTo("BRL");
    assertThat(found.getStatus()).isEqualTo(Account.Status.ACTIVE);
  }
}
