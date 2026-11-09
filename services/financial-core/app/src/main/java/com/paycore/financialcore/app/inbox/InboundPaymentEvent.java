package com.paycore.financialcore.app.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

/** Envelope recebido do Kafka, já validado nos campos de roteamento. */
public record InboundPaymentEvent(
    UUID eventId,
    String eventType,
    int eventVersion,
    UUID aggregateId,
    UUID correlationId,
    Instant occurredAt,
    String payload,
    JsonNode data) {

  public static InboundPaymentEvent fromJson(String payload, ObjectMapper objectMapper) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      return new InboundPaymentEvent(
          UUID.fromString(root.path("event_id").asText()),
          root.path("event_type").asText(),
          root.path("event_version").asInt(),
          UUID.fromString(root.path("aggregate").path("id").asText()),
          UUID.fromString(root.path("correlation_id").asText()),
          Instant.parse(root.path("occurred_at").asText()),
          payload,
          root.path("data"));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("invalid payment event: " + exception.getMessage(), exception);
    }
  }

  public long amount() {
    if (!data.hasNonNull("amount") || !data.path("amount").canConvertToLong()) {
      throw new IllegalArgumentException("payment event " + eventId + " has no valid amount");
    }
    return data.path("amount").asLong();
  }

  public String currency() {
    String currency = data.path("currency").asText("");
    if (currency.length() != 3) {
      throw new IllegalArgumentException("payment event " + eventId + " has no valid currency");
    }
    return currency;
  }
}
