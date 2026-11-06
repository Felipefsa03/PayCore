package com.paycore.financialcore.app.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emite tokens HS256 para testes de integração, simulando o provedor de
 * identidade externo. Nunca é usado em código de produção.
 */
public final class TestJwt {

  private TestJwt() {
    // utility class
  }

  public static String forOwner(String secret, String issuer, UUID ownerId) {
    return token(secret, issuer, ownerId, Instant.now().plusSeconds(3_600));
  }

  public static String expired(String secret, String issuer, UUID ownerId) {
    return token(secret, issuer, ownerId, Instant.now().minusSeconds(60));
  }

  public static String withSubject(String secret, String issuer, String subject) {
    return token(secret, issuer, subject, Instant.now().plusSeconds(3_600));
  }

  private static String token(
      String secret, String issuer, Object subject, Instant expiresAt) {
    try {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(subject.toString())
              .issuer(issuer)
              .issueTime(Date.from(Instant.now().minusSeconds(5)))
              .expirationTime(Date.from(expiresAt))
              .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
      return jwt.serialize();
    } catch (Exception exception) {
      throw new IllegalStateException("não foi possível emitir o token de teste", exception);
    }
  }
}
