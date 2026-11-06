package com.paycore.financialcore.app.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para testes de integração com Postgres real e migrations Flyway.
 *
 * <p>Por padrão sobe um container PostgreSQL via Testcontainers (mesmo caminho
 * do CI). Em ambientes sem Docker, defina {@code PAYCORE_IT_JDBC_URL} (e
 * opcionalmente {@code PAYCORE_IT_DB_USER}/{@code PAYCORE_IT_DB_PASSWORD})
 * para reutilizar um Postgres local.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractPostgresIntegrationTest {

  private static final String EXTERNAL_URL = System.getenv("PAYCORE_IT_JDBC_URL");

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("paycore_it")
          .withUsername("paycore")
          .withPassword("paycore");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    if (EXTERNAL_URL != null) {
      registry.add("spring.datasource.url", () -> EXTERNAL_URL);
      registry.add("spring.datasource.username", () -> env("PAYCORE_IT_DB_USER", "paycore"));
      registry.add("spring.datasource.password", () -> env("PAYCORE_IT_DB_PASSWORD", "paycore"));
      return;
    }
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired protected MockMvc mvc;

  @Autowired protected ObjectMapper objectMapper;

  @Value("${paycore.auth.jwt.secret}")
  protected String jwtSecret;

  @Value("${paycore.auth.jwt.issuer}")
  protected String jwtIssuer;

  protected String tokenFor(UUID ownerId) {
    return TestJwt.forOwner(jwtSecret, jwtIssuer, ownerId);
  }

  protected static String bearer(String token) {
    return "Bearer " + token;
  }

  private static String env(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }
}
