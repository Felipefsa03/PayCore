package com.paycore.financialcore.app.outbox.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paycore.financialcore.payments.OutboxEvent;

/**
 * Monta o envelope publicado no Kafka.
 *
 * <p>O envelope carrega metadados de roteamento e deduplicação
 * ({@code event_id}, {@code event_type}, {@code event_version},
 * {@code correlation_id}, {@code aggregate}) e o payload de domínio em
 * {@code data}. O formato é validado contra
 * {@code contracts/events/*.schema.json}.
 */
public final class PaymentEventEnvelope {

  private PaymentEventEnvelope() {
    // utility class
  }

  public static String toJson(OutboxEvent event, EventContract contract, ObjectMapper objectMapper) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("event_id", event.getId().toString());
    root.put("event_type", contract.getContractType());
    root.put("event_version", contract.getVersion());
    root.put("occurred_at", event.getCreatedAt().toString());
    root.put("correlation_id", event.getCorrelationId().toString());

    ObjectNode aggregate = root.putObject("aggregate");
    aggregate.put("type", "payment");
    aggregate.put("id", event.getAggregateId().toString());

    try {
      root.set("data", objectMapper.readTree(event.getPayload()));
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "invalid payload for outbox event " + event.getId(), exception);
    }
  }
}
