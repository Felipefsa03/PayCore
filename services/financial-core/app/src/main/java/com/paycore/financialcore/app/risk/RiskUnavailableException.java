package com.paycore.financialcore.app.risk;

public class RiskUnavailableException extends RuntimeException {
  public RiskUnavailableException(String message) {
    super(message);
  }

  public RiskUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
