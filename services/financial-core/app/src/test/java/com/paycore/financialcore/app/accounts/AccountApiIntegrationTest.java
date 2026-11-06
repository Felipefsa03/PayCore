package com.paycore.financialcore.app.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.paycore.financialcore.app.support.AbstractPostgresIntegrationTest;
import com.paycore.financialcore.app.support.TestJwt;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contrato HTTP e autorização por proprietário (RF-01, RF-02, RNF-06),
 * incluindo o cenário de confirmação do card: dois usuários distintos não
 * compartilham contas e recebem 403/404 consistentes.
 */
class AccountApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void rejectsRequestWithoutToken() throws Exception {
    mvc.perform(
            post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"BRL\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  @Test
  void rejectsExpiredToken() throws Exception {
    UUID ownerId = UUID.randomUUID();
    String expired = TestJwt.expired(jwtSecret, jwtIssuer, ownerId);
    mvc.perform(
            post("/accounts")
                .header("Authorization", bearer(expired))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"BRL\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  @Test
  void rejectsTokenWhoseSubjectIsNotAnOwnerUuid() throws Exception {
    String token = TestJwt.withSubject(jwtSecret, jwtIssuer, "not-a-uuid");
    mvc.perform(
            get("/accounts/{id}", UUID.randomUUID())
                .header("Authorization", bearer(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  @Test
  void rejectsTokenSignedWithAnotherSecret() throws Exception {
    String forged =
        TestJwt.forOwner(
            "another-secret-with-at-least-32-bytes-long",
            jwtIssuer,
            UUID.randomUUID());
    mvc.perform(
            get("/accounts/{id}", UUID.randomUUID())
                .header("Authorization", bearer(forged)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  @Test
  void createsAccountOwnedByTheTokenSubject() throws Exception {
    UUID ownerId = UUID.randomUUID();

    mvc.perform(
            post("/accounts")
                .header("Authorization", bearer(tokenFor(ownerId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"BRL\",\"overdraftPolicy\":\"allowed\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
        .andExpect(jsonPath("$.currency").value("BRL"))
        .andExpect(jsonPath("$.overdraftPolicy").value("allowed"))
        .andExpect(jsonPath("$.status").value("active"))
        .andExpect(jsonPath("$.availableBalance").value(0));
  }

  @Test
  void rejectsInvalidCurrencyWithStandardError() throws Exception {
    mvc.perform(
            post("/accounts")
                .header("Authorization", bearer(tokenFor(UUID.randomUUID())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"brl\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"))
        .andExpect(jsonPath("$.details.fields.currency").exists());
  }

  @Test
  void otherOwnerCannotReadTheAccount() throws Exception {
    UUID alice = UUID.randomUUID();
    UUID bob = UUID.randomUUID();
    UUID accountId = createAccount(alice);

    mvc.perform(get("/accounts/{id}", accountId).header("Authorization", bearer(tokenFor(bob))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"));

    mvc.perform(
            get("/accounts/{id}/postings", accountId)
                .header("Authorization", bearer(tokenFor(bob))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("forbidden"));

    mvc.perform(
            get("/accounts/{id}", accountId).header("Authorization", bearer(tokenFor(alice))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(accountId.toString()));
  }

  @Test
  void unknownAccountReturnsNotFound() throws Exception {
    mvc.perform(
            get("/accounts/{id}", UUID.randomUUID())
                .header("Authorization", bearer(tokenFor(UUID.randomUUID()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("account_not_found"));
  }

  @Test
  void computesBalanceFromPostingsAndPaginatesHistory() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID accountId = createAccount(ownerId);
    Instant base = Instant.now().minus(10, ChronoUnit.MINUTES);
    recordPosting(accountId, "CREDIT", 100, base);
    recordPosting(accountId, "CREDIT", 50, base.plusSeconds(60));
    recordPosting(accountId, "DEBIT", 30, base.plusSeconds(120));

    mvc.perform(
            get("/accounts/{id}", accountId)
                .header("Authorization", bearer(tokenFor(ownerId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableBalance").value(120));

    MvcResult firstPage =
        mvc.perform(
                get("/accounts/{id}/postings", accountId)
                    .param("limit", "2")
                    .header("Authorization", bearer(tokenFor(ownerId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
            .andExpect(jsonPath("$.items[1].direction").value("CREDIT"))
            .andExpect(jsonPath("$.nextCursor").isNotEmpty())
            .andReturn();

    JsonNode page = objectMapper.readTree(firstPage.getResponse().getContentAsString());
    String cursor = page.get("nextCursor").asText();

    mvc.perform(
            get("/accounts/{id}/postings", accountId)
                .param("limit", "2")
                .param("cursor", cursor)
                .header("Authorization", bearer(tokenFor(ownerId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.nextCursor").isEmpty());
  }

  @Test
  void rejectsInvalidCursor() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID accountId = createAccount(ownerId);

    mvc.perform(
            get("/accounts/{id}/postings", accountId)
                .param("cursor", "not-a-uuid")
                .header("Authorization", bearer(tokenFor(ownerId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_error"));
  }

  private UUID createAccount(UUID ownerId) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/accounts")
                    .header("Authorization", bearer(tokenFor(ownerId)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currency\":\"BRL\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    UUID accountId = UUID.fromString(body.get("id").asText());
    assertThat(body.get("ownerId").asText()).isEqualTo(ownerId.toString());
    return accountId;
  }

  private static final UUID OFFSET_ACCOUNT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  private void ensureOffsetAccount() {
    jdbc.update(
        "INSERT INTO accounts (id, owner_id, currency, overdraft_policy, status, created_at) "
            + "VALUES (?, ?, 'BRL', 'FORBIDDEN', 'ACTIVE', now()) ON CONFLICT (id) DO NOTHING",
        OFFSET_ACCOUNT_ID,
        UUID.fromString("00000000-0000-0000-0000-000000000000"));
  }

  private void recordPosting(UUID accountId, String direction, long amount, Instant createdAt) {
    ensureOffsetAccount();
    UUID journalEntryId = UUID.randomUUID();
    java.sql.Timestamp timestamp = java.sql.Timestamp.from(createdAt);
    new TransactionTemplate(transactionManager).execute(status -> {
      jdbc.update(
          "INSERT INTO journal_entries (id, transfer_id, currency, created_at) VALUES (?, ?, ?, ?)",
          journalEntryId,
          UUID.randomUUID(),
          "BRL",
          timestamp);
      String opposite = direction.equals("CREDIT") ? "DEBIT" : "CREDIT";
      jdbc.update(
          "INSERT INTO postings (id, journal_entry_id, account_id, direction, amount, currency, "
              + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
          UUID.randomUUID(),
          journalEntryId,
          accountId,
          direction,
          amount,
          "BRL",
          timestamp);
      jdbc.update(
          "INSERT INTO postings (id, journal_entry_id, account_id, direction, amount, currency, "
              + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
          UUID.randomUUID(),
          journalEntryId,
          OFFSET_ACCOUNT_ID,
          opposite,
          amount,
          "BRL",
          timestamp);
      return null;
    });
  }
}
