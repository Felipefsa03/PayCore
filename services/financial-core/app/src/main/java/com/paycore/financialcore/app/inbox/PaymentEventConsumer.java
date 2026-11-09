package com.paycore.financialcore.app.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.financialcore.app.outbox.kafka.TopicNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor dos eventos de pagamento. A entrega é at-least-once; a
 * deduplicação acontece na inbox. Falhas não são confirmadas ao broker, então o
 * evento é reentregue (o dead-letter entra no card de webhooks/DLQ).
 */
@Component
@ConditionalOnProperty(prefix = "paycore.inbox", name = "consumer-enabled", havingValue = "true")
public class PaymentEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

  private final InboxService inbox;

  private final ObjectMapper objectMapper;

  public PaymentEventConsumer(InboxService inbox, ObjectMapper objectMapper) {
    this.inbox = inbox;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = {TopicNames.PAYMENT_COMPLETED_V1, TopicNames.PAYMENT_REJECTED_V1},
      groupId = "${paycore.inbox.consumer-group:paycore-inbox}")
  public void onEvent(ConsumerRecord<String, String> record) {
    InboundPaymentEvent event;
    try {
      event = InboundPaymentEvent.fromJson(record.value(), objectMapper);
    } catch (RuntimeException exception) {
      log.error(
          "invalid payment event on {}-{} offset {}: {}",
          record.topic(),
          record.partition(),
          record.offset(),
          exception.getMessage());
      throw exception;
    }

    InboxResult result = inbox.handle(event, record.topic(), record.partition(), record.offset());
    if (result.duplicate()) {
      log.info(
          "duplicate delivery ignored eventId={} topic={} offset={}",
          event.eventId(),
          record.topic(),
          record.offset());
    } else {
      log.debug(
          "inbox processed eventId={} outcome={} topic={} offset={}",
          event.eventId(),
          result.outcome(),
          record.topic(),
          record.offset());
    }
  }
}
