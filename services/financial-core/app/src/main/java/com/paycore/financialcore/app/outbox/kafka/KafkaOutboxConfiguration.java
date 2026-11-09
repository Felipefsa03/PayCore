package com.paycore.financialcore.app.outbox.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paycore.financialcore.app.outbox.DomainEventPublisher;
import com.paycore.financialcore.app.outbox.OutboxProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Ativa o transporte Kafka do outbox.
 *
 * <p>Só é carregada com {@code paycore.outbox.transport=kafka}. Os tópicos são
 * declarados com três partições e uma réplica (laboratório local); a chave de
 * partição é o id do pagamento. Em produção o fator de réplica é definido pelo
 * cluster, não pela aplicação.
 */
@Configuration
@ConditionalOnProperty(prefix = "paycore.outbox", name = "transport", havingValue = "kafka")
public class KafkaOutboxConfiguration {

  @Bean
  DomainEventPublisher kafkaDomainEventPublisher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper objectMapper,
      OutboxProperties properties) {
    return new KafkaDomainEventPublisher(kafka, objectMapper, properties.getKafkaSendTimeoutMs());
  }

  @Bean
  NewTopic paymentCompletedTopic() {
    return TopicBuilder.name(TopicNames.PAYMENT_COMPLETED_V1).partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic paymentRejectedTopic() {
    return TopicBuilder.name(TopicNames.PAYMENT_REJECTED_V1).partitions(3).replicas(1).build();
  }
}
