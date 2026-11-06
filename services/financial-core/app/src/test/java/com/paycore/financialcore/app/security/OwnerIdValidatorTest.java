package com.paycore.financialcore.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** Valida que o claim {@code sub} identifica um proprietário (UUID). */
class OwnerIdValidatorTest {

  private final OwnerIdValidator validator = new OwnerIdValidator();

  @Test
  void acceptsUuidSubject() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
        .subject(UUID.randomUUID().toString()).build();

    assertThat(validator.validate(jwt).hasErrors()).isFalse();
  }

  @Test
  void rejectsSubjectThatIsNotUuid() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("alice").build();

    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }

  @Test
  void rejectsMissingSubject() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
        .claim("iss", "paycore-lab").build();

    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }
}
