package com.paycore.financialcore.shared;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Valor monetário em centavos inteiros, com moeda ISO 4217. Elimina erros de
 * arredondamento de ponto flutuante (RFC-001 §8.3).
 */
public final class Money {

  private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

  private final long amountCents;
  private final String currency;

  private Money(long amountCents, String currency) {
    this.amountCents = amountCents;
    this.currency = currency;
  }

  public static Money of(long amountCents, String currency) {
    Objects.requireNonNull(currency, "currency must not be null");
    if (amountCents <= 0) {
      throw new IllegalArgumentException("amountCents must be greater than zero");
    }
    if (!CURRENCY_PATTERN.matcher(currency).matches()) {
      throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code");
    }
    return new Money(amountCents, currency);
  }

  public long amountCents() {
    return amountCents;
  }

  public String currency() {
    return currency;
  }

  public boolean sameCurrencyAs(Money other) {
    return currency.equals(other.currency);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Money other)) {
      return false;
    }
    return amountCents == other.amountCents && currency.equals(other.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amountCents, currency);
  }

  @Override
  public String toString() {
    return amountCents + " " + currency;
  }
}
