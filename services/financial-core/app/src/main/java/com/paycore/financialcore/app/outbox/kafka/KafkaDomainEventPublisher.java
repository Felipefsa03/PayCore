package com.paycore.financialcore.app.outbox.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.financialcore.app.outbox.DomainEventPublisher;
import com.paycore.financialcore.payments.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publica eventos de pagamento em tópicos versionados do Kafka.
 *
 * <p>A chave da mensagem é o {@code aggregate.id} (id do pagamento), o que
 * preserva a ordem por pagamento dentro da partição. O envio é aguardado: se o
 * broker não confirmar dentro do timeout, o evento permanece pendente no outbox
 * e o dispatcher agenda retry.
 */
public final class KafkaDomainEventPublisher implements DomainEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(KafkaDomainEventPublisher.class);

  private static final String CONTENT_TYPE = "application/json";

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;
  private final long sendTimeoutMs;

  public KafkaDomainEventPublisher(
      KafkaTemplate<String, String> kafka, ObjectMapper objectMapper, long sendTimeoutMs) {
    if (sendTimeoutMs <= 0) {
      throw new IllegalArgumentException("sendTimeoutMs must be positive");
    }
    this.kafka = kafka;
    this.objectMapper = objectMapper;
    this.sendTimeoutMs = sendTimeoutMs;
  }

  @Override
  public void publish(OutboxEvent event) {
    EventContract contract = EventContract.forOutboxEventType(event.getEventType());
    String key = event.getAggregateId().toString();
    String payload = PaymentEventEnvelope.toJson(event, contract, objectMapper);

    ProducerRecord<String, String> record =
        new ProducerRecord<>(contract.getTopic(), key, payload);
    Headers headers = record.headers();
    headers.add("event_id", utf8(event.getId().toString()));
    headers.add("event_type", utf8(contract.getContractType()));
    headers.add("event_version", utf8(Integer.toString(contract.getVersion())));
    headers.add("correlation_id", utf8(event.getCorrelationId().toString()));
    headers.add("content_type", utf8(CONTENT_TYPE));

    try {
      SendResult<String, String> result = kafka.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
      log.debug(
          "published outbox event {} to {}-{} at offset {}",
          event.getId(),
          result.getRecordMetadata().topic(),
          result.getRecordMetadata().partition(),
          result.getRecordMetadata().offset());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "interrupted while publishing outbox event " + event.getId(), exception);
    } catch (ExecutionException | TimeoutException exception) {
      throw new IllegalStateException(
          "kafka publish failed for outbox event " + event.getId() + ": " + exception.getMessage(),
          exception);
    }
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
