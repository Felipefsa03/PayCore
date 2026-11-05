package com.paycore.financialcore.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Prova de que o monólito modular sobe contra um Postgres real, aplica as
 * migrations Flyway, e que os módulos accounts/ledger/payments estão
 * corretamente ligados em um único contexto Spring (confirmação do card de
 * bootstrap: "docker compose up funciona, migrations aplicam e o CI fica
 * verde"). O container é gerenciado manualmente (padrão "singleton
 * container" do Testcontainers) em vez de usar @Testcontainers/@Container,
 * para evitar combinar duas extensões JUnit 5 na mesma classe de teste.
 */
@SpringBootTest(classes = FinancialCoreApplication.class)
class FinancialCoreApplicationTest {

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("paycore_it")
          .withUsername("paycore")
          .withPassword("paycore");

  @BeforeAll
  static void startContainer() {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void registerDataSource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private AccountRepository accountRepository;

  @Test
  void bootsAndAppliesFlywayMigrations() {
    assertThat(accountRepository.findAll()).isEmpty();
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
