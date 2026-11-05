package com.paycore.financialcore.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void createsValidMoney() {
    Money money = Money.of(1_000, "BRL");

    assertEquals(1_000, money.amountCents());
    assertEquals("BRL", money.currency());
  }

  @Test
  void rejectsZeroOrNegativeAmount() {
    assertThrows(IllegalArgumentException.class, () -> Money.of(0, "BRL"));
    assertThrows(IllegalArgumentException.class, () -> Money.of(-100, "BRL"));
  }

  @Test
  void rejectsInvalidCurrencyCode() {
    assertThrows(IllegalArgumentException.class, () -> Money.of(100, "brl"));
    assertThrows(IllegalArgumentException.class, () -> Money.of(100, "REAL"));
  }

  @Test
  void comparesCurrencyAcrossInstances() {
    Money inBrl = Money.of(100, "BRL");
    Money otherInBrl = Money.of(500, "BRL");
    Money inUsd = Money.of(100, "USD");

    assertTrue(inBrl.sameCurrencyAs(otherInBrl));
    assertFalse(inBrl.sameCurrencyAs(inUsd));
  }
}
