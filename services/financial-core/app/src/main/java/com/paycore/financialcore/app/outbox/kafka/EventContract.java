package com.paycore.financialcore.app.outbox.kafka;

import java.util.Arrays;

/**
 * Mapeia o evento interno do outbox para o contrato externo versionado.
 *
 * <p>O domínio grava {@code transfer.completed}/{@code transfer.rejected}; a
 * interface assíncrona expõe {@code payment.completed}/{@code payment.rejected}
 * em tópicos versionados. Essa camada evita que uma mudança interna de nome de
 * evento quebre parceiros.
 */
public enum EventContract {
  PAYMENT_COMPLETED(
      "transfer.completed", "payment.completed", 1, TopicNames.PAYMENT_COMPLETED_V1),
  PAYMENT_REJECTED("transfer.rejected", "payment.rejected", 1, TopicNames.PAYMENT_REJECTED_V1);

  private final String outboxEventType;
  private final String contractType;
  private final int version;
  private final String topic;

  EventContract(String outboxEventType, String contractType, int version, String topic) {
    this.outboxEventType = outboxEventType;
    this.contractType = contractType;
    this.version = version;
    this.topic = topic;
  }

  public static EventContract forOutboxEventType(String outboxEventType) {
    return Arrays.stream(values())
        .filter(contract -> contract.outboxEventType.equals(outboxEventType))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("no event contract for outbox type: " + outboxEventType));
  }

  public String getOutboxEventType() {
    return outboxEventType;
  }

  public String getContractType() {
    return contractType;
  }

  public int getVersion() {
    return version;
  }

  public String getTopic() {
    return topic;
  }
}
