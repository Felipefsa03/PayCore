package com.paycore.financialcore.app.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Erro padronizado da API (contrato OpenAPI {@code Error}). {@code code} é
 * estável entre versões e é o campo que clientes devem usar para decisões.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, Object> details) {

  public static ApiError of(String code, String message) {
    return new ApiError(code, message, null);
  }
}
