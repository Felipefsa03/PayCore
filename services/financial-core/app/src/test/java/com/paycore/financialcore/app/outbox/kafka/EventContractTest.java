package com.paycore.financialcore.app.outbox.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.financialcore.payments.OutboxEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventContractTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String COMPLETED_SCHEMA = "payment-completed.v1.schema.json";

  private static final String REJECTED_SCHEMA = "payment-rejected.v1.schema.json";

  @Test
  void mapsInternalEventsToVersionedContracts() {
    EventContract completed = EventContract.forOutboxEventType("transfer.completed");
    EventContract rejected = EventContract.forOutboxEventType("transfer.rejected");

    assertThat(completed.getContractType()).isEqualTo("payment.completed");
    assertThat(completed.getVersion()).isEqualTo(1);
    assertThat(completed.getTopic()).isEqualTo(TopicNames.PAYMENT_COMPLETED_V1);

    assertThat(rejected.getContractType()).isEqualTo("payment.rejected");
    assertThat(rejected.getVersion()).isEqualTo(1);
    assertThat(rejected.getTopic()).isEqualTo(TopicNames.PAYMENT_REJECTED_V1);

    assertThat(TopicNames.PAYMENT_COMPLETED_V1).endsWith(".v1");
    assertThat(TopicNames.PAYMENT_REJECTED_V1).endsWith(".v1");
  }

  @Test
  void rejectsUnknownInternalEventType() {
    assertThatThrownBy(() -> EventContract.forOutboxEventType("transfer.unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("transfer.unknown");
  }

  @Test
  void buildsCompletedEnvelopeValidatedByTheSchema() {
    UUID transferId = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();
    OutboxEvent event =
        new OutboxEvent(
            UUID.randomUUID(),
            "transfer",
            transferId,
            "transfer.completed",
            1,
            completedPayload(transferId, correlationId),
            correlationId);

    String json = PaymentEventEnvelope.toJson(event, EventContract.PAYMENT_COMPLETED, MAPPER);
    EventContracts.assertMatchesSchema(COMPLETED_SCHEMA, json);

    JsonNode envelope = EventContracts.parse(json);
    assertThat(envelope.path("event_id").asText()).isEqualTo(event.getId().toString());
    assertThat(envelope.path("event_type").asText()).isEqualTo("payment.completed");
    assertThat(envelope.path("event_version").asInt()).isEqualTo(1);
    assertThat(envelope.path("aggregate").path("type").asText()).isEqualTo("payment");
    assertThat(envelope.path("aggregate").path("id").asText()).isEqualTo(transferId.toString());
    assertThat(envelope.path("data").path("state").asText()).isEqualTo("COMPLETED");
    assertThat(envelope.path("data").path("amount").asLong()).isEqualTo(1_500L);
  }

  @Test
  void buildsRejectedEnvelopeValidatedByTheSchema() {
    UUID transferId = UUID.randomUUID();
    UUID correlationId = UUID.randomUUID();
    OutboxEvent event =
        new OutboxEvent(
            UUID.randomUUID(),
            "transfer",
            transferId,
            "transfer.rejected",
            1,
            rejectedPayload(transferId, correlationId),
            correlationId);

    String json = PaymentEventEnvelope.toJson(event, EventContract.PAYMENT_REJECTED, MAPPER);
    EventContracts.assertMatchesSchema(REJECTED_SCHEMA, json);

    JsonNode envelope = EventContracts.parse(json);
    assertThat(envelope.path("event_type").asText()).isEqualTo("payment.rejected");
    assertThat(envelope.path("data").path("state").asText()).isEqualTo("REJECTED");
    assertThat(envelope.path("data").path("failureReason").asText())
        .isEqualTo("insufficient_funds");
  }

  @Test
  void schemasAreStrictAndDeclareTheirVersion() {
    JsonNode completed = EventContracts.schema(COMPLETED_SCHEMA);
    JsonNode rejected = EventContracts.schema(REJECTED_SCHEMA);

    assertThat(completed.path("$id").asText()).endsWith(COMPLETED_SCHEMA);
    assertThat(rejected.path("$id").asText()).endsWith(REJECTED_SCHEMA);

    assertThat(completed.path("additionalProperties").asBoolean()).isFalse();
    assertThat(rejected.path("additionalProperties").asBoolean()).isFalse();

    assertThat(completed.path("properties").path("event_type").path("const").asText())
        .isEqualTo("payment.completed");
    assertThat(rejected.path("properties").path("event_type").path("const").asText())
        .isEqualTo("payment.rejected");
    assertThat(completed.path("properties").path("event_version").path("const").asInt())
        .isEqualTo(1);
    assertThat(rejected.path("properties").path("event_version").path("const").asInt())
        .isEqualTo(1);
  }

  private static String completedPayload(UUID transferId, UUID correlationId) {
    return "{"
        + "\"transferId\":\""
        + transferId
        + "\","
        + "\"state\":\"COMPLETED\","
        + "\"sourceAccountId\":\""
        + UUID.randomUUID()
        + "\","
        + "\"destinationAccountId\":\""
        + UUID.randomUUID()
        + "\","
        + "\"amount\":1500,"
        + "\"currency\":\"BRL\","
        + "\"correlationId\":\""
        + correlationId
        + "\","
        + "\"journalEntryId\":\""
        + UUID.randomUUID()
        + "\""
        + "}";
  }

  private static String rejectedPayload(UUID transferId, UUID correlationId) {
    return "{"
        + "\"transferId\":\""
        + transferId
        + "\","
        + "\"state\":\"REJECTED\","
        + "\"sourceAccountId\":\""
        + UUID.randomUUID()
        + "\","
        + "\"destinationAccountId\":\""
        + UUID.randomUUID()
        + "\","
        + "\"amount\":1500,"
        + "\"currency\":\"BRL\","
        + "\"correlationId\":\""
        + correlationId
        + "\","
        + "\"failureReason\":\"insufficient_funds\""
        + "}";
  }
}
