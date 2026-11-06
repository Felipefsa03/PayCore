package com.paycore.financialcore.app.api.accounts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo de {@code POST /accounts} (contrato OpenAPI CreateAccountRequest).
 * A moeda é validada na borda; a política de descoberto tem default
 * {@code forbidden} (I-05).
 */
public record CreateAccountRequest(
    @NotBlank(message = "currency é obrigatório")
        @Pattern(
            regexp = "^[A-Z]{3}$",
            message = "currency deve ser um código ISO 4217 de 3 letras maiúsculas")
        String currency,
    @Pattern(regexp = "^(forbidden|allowed)$", message = "overdraftPolicy deve ser forbidden ou allowed")
        String overdraftPolicy) {}
