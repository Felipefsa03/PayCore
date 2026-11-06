package com.paycore.financialcore.app.api;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Exceção com resposta HTTP padronizada. Permite que qualquer camada lance um
 * erro de contrato sem depender de detalhes de serialização.
 */
public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final transient Map<String, Object> details;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, null);
  }

  public ApiException(
      HttpStatus status, String code, String message, Map<String, Object> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
