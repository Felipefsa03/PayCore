package com.paycore.financialcore.app.security;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Exige que o {@code sub} do token seja um UUID de proprietário. Sem isso, um
 * token válido com {@code sub} arbitrário causaria erro de conversão na
 * camada de API em vez de 401.
 */
public final class OwnerIdValidator implements OAuth2TokenValidator<Jwt> {

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    String subject = token.getSubject();
    try {
      UUID.fromString(Objects.requireNonNull(subject, "sub"));
      return OAuth2TokenValidatorResult.success();
    } catch (RuntimeException exception) {
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "O claim sub deve ser um UUID de proprietário.", null));
    }
  }
}
