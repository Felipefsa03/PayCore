package com.paycore.financialcore.app.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfiguration {

  /**
   * Transporte padrão até a integração com Kafka. Testes e ambientes podem
   * fornecer outro {@link DomainEventPublisher} sem alterar o publicador.
   */
  @Bean
  @ConditionalOnMissingBean(DomainEventPublisher.class)
  DomainEventPublisher loggingDomainEventPublisher() {
    return new LoggingDomainEventPublisher();
  }
}
