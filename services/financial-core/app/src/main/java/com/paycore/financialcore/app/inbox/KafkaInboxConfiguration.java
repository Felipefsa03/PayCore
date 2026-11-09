package com.paycore.financialcore.app.inbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Habilita os listeners de Kafka apenas quando o consumidor é ligado
 * explicitamente ({@code paycore.inbox.consumer-enabled=true}), evitando que
 * contextos sem broker tentem conectar durante os testes.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "paycore.inbox", name = "consumer-enabled", havingValue = "true")
public class KafkaInboxConfiguration {

  @Bean
  DefaultErrorHandler kafkaErrorHandler() {
    return new DefaultErrorHandler(new FixedBackOff(1_000L, 3));
  }
}
