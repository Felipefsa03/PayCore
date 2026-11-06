package com.paycore.financialcore.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.financialcore.app.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Serializa 401/403 no mesmo formato de erro do restante da API, evitando o
 * corpo HTML padrão do Spring Security (RNF-06).
 */
@Component
public class SecurityErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public SecurityErrorWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException exception)
      throws IOException {
    write(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthenticated",
        "Credencial ausente ou inválida.");
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException exception)
      throws IOException {
    write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden",
        "Acesso negado ao recurso solicitado.");
  }

  private void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, message));
  }
}
