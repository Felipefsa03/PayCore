package com.paycore.financialcore.app;

import com.paycore.financialcore.app.outbox.OutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Composição do monólito modular: escaneia entidades, repositórios e serviços
 * de accounts, ledger e payments em um único runtime Spring Boot (RFC-001 §7.2).
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.paycore.financialcore")
@EntityScan(basePackages = "com.paycore.financialcore")
@EnableJpaRepositories(basePackages = "com.paycore.financialcore")
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class FinancialCoreApplication {

  public static void main(String[] args) {
    SpringApplication.run(FinancialCoreApplication.class, args);
  }
}
