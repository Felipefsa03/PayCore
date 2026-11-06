package com.paycore.financialcore.app.api.accounts;

import java.time.Instant;
import java.util.UUID;

/** Representação pública de conta (contrato OpenAPI Account). */
public record AccountResponse(
    UUID id,
    UUID ownerId,
    String currency,
    String overdraftPolicy,
    String status,
    long availableBalance,
    Instant createdAt) {}
