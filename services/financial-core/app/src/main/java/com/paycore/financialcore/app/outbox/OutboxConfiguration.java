package com.paycore.financialcore.app.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfiguration {

  /**
   * Transporte padrão até a integração com Kafka. Testes e ambientes podem
   * fornecer outro {@link DomainEventPublisher} sem alterar o publicador.
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "paycore.outbox",
      name = "transport",
      havingValue = "log",
      matchIfMissing = true)
  DomainEventPublisher loggingDomainEventPublisher() {
    return new LoggingDomainEventPublisher();
  }
}
