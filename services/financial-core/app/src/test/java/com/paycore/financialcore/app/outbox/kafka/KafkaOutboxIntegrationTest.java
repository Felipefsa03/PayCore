package com.paycore.financialcore.app.outbox.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.paycore.financialcore.accounts.Account;
import com.paycore.financialcore.accounts.AccountService;
import com.paycore.financialcore.app.outbox.DomainEventPublisher;
import com.paycore.financialcore.app.outbox.OutboxDispatcher;
import com.paycore.financialcore.app.support.AbstractPostgresIntegrationTest;
import com.paycore.financialcore.app.transfers.TransferService;
import com.paycore.financialcore.ledger.LedgerService;
import com.paycore.financialcore.payments.OutboxEventRepository;
import com.paycore.financialcore.payments.Transfer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

@EmbeddedKafka(
    partitions = 3,
    topics = {TopicNames.PAYMENT_COMPLETED_V1, TopicNames.PAYMENT_REJECTED_V1})
@TestPropertySource(
    properties = {
      "paycore.outbox.scheduled=false",
      "paycore.outbox.transport=kafka",
      "paycore.outbox.batch-size=100",
      "paycore.outbox.kafka-send-timeout-ms=15000",
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "logging.level.com.paycore.financialcore.app.outbox.kafka=DEBUG"
    })
class KafkaOutboxIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AccountService accounts;
  @Autowired private LedgerService ledger;
  @Autowired private TransferService transfers;
  @Autowired private OutboxEventRepository outbox;
  @Autowired private OutboxDispatcher dispatcher;
  @Autowired private DomainEventPublisher publisher;
  @Autowired private EmbeddedKafkaBroker embeddedKafka;

  @BeforeEach
  void clearOutbox() {
    outbox.deleteAllInBatch();
  }

  @Test
  void publishesCompletedEventToVersionedTopicKeyedByPayment() {
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 1_000L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    UUID correlationId = UUID.randomUUID();

    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            250L,
            "BRL",
            "key-" + UUID.randomUUID(),
            correlationId);

    assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

    ConsumerRecord<String, String> record =
        consumeSingleRecord(TopicNames.PAYMENT_COMPLETED_V1);
    assertThat(record.topic()).isEqualTo(TopicNames.PAYMENT_COMPLETED_V1);
    assertThat(record.key()).isEqualTo(transfer.getId().toString());
    assertThat(header(record, "event_type")).isEqualTo("payment.completed");
    assertThat(header(record, "event_version")).isEqualTo("1");
    assertThat(header(record, "correlation_id")).isEqualTo(correlationId.toString());
    assertThat(header(record, "content_type")).isEqualTo("application/json");
    assertThat(header(record, "event_id")).isNotBlank();

    EventContracts.assertMatchesSchema("payment-completed.v1.schema.json", record.value());
    JsonNode envelope = EventContracts.parse(record.value());
    assertThat(envelope.path("event_type").asText()).isEqualTo("payment.completed");
    assertThat(envelope.path("aggregate").path("id").asText())
        .isEqualTo(transfer.getId().toString());
    assertThat(envelope.path("data").path("transferId").asText())
        .isEqualTo(transfer.getId().toString());
    assertThat(envelope.path("data").path("amount").asLong()).isEqualTo(250L);
  }

  @Test
  void publishesRejectedEventToVersionedTopicKeyedByPayment() {
    assertThat(publisher).isInstanceOf(KafkaDomainEventPublisher.class);
    UUID owner = UUID.randomUUID();
    Account source = fundedAccount(owner, 50L);
    Account destination = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);

    Transfer transfer =
        transfers.transfer(
            owner,
            source.getId(),
            destination.getId(),
            100L,
            "BRL",
            "key-" + UUID.randomUUID(),
            UUID.randomUUID());

    assertThat(dispatcher.dispatchOnce()).isEqualTo(1);

    ConsumerRecord<String, String> record = consumeSingleRecord(TopicNames.PAYMENT_REJECTED_V1);
    assertThat(record.key()).isEqualTo(transfer.getId().toString());
    assertThat(header(record, "event_type")).isEqualTo("payment.rejected");

    EventContracts.assertMatchesSchema("payment-rejected.v1.schema.json", record.value());
    JsonNode envelope = EventContracts.parse(record.value());
    assertThat(envelope.path("data").path("failureReason").asText())
        .isEqualTo("insufficient_funds");
  }

  private ConsumerRecord<String, String> consumeSingleRecord(String topic) {
    Map<String, Object> properties =
        KafkaTestUtils.consumerProps("outbox-test-" + UUID.randomUUID(), "false", embeddedKafka);
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<String, String>(properties).createConsumer()) {
      embeddedKafka.consumeFromAnEmbeddedTopic(consumer, topic);
      long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
      while (System.nanoTime() < deadline) {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
          if (topic.equals(record.topic())) {
            return record;
          }
        }
      }
      String diagnostics =
          "assignment="
              + consumer.assignment()
              + " begin="
              + consumer.beginningOffsets(consumer.assignment())
              + " end="
              + consumer.endOffsets(consumer.assignment());
      throw new AssertionError("no record on " + topic + " " + diagnostics);
    }
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    Header found = record.headers().lastHeader(name);
    assertThat(found).as("missing header %s", name).isNotNull();
    return new String(found.value(), StandardCharsets.UTF_8);
  }

  private Account fundedAccount(UUID owner, long amount) {
    Account account = accounts.create(owner, "BRL", Account.OverdraftPolicy.FORBIDDEN);
    Account system = accounts.create(owner, "BRL", Account.OverdraftPolicy.ALLOWED);
    ledger.record(UUID.randomUUID(), system.getId(), account.getId(), amount, "BRL");
    return account;
  }
}
