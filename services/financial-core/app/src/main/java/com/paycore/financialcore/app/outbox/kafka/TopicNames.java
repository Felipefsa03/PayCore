package com.paycore.financialcore.app.outbox.kafka;

/** Nomes dos tópicos versionados publicados pelo PayCore. */
public final class TopicNames {

  public static final String PAYMENT_COMPLETED_V1 = "paycore.payments.payment-completed.v1";

  public static final String PAYMENT_REJECTED_V1 = "paycore.payments.payment-rejected.v1";

  private TopicNames() {
    // constants only
  }
}
