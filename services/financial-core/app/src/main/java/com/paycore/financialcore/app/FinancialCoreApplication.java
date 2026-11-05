package com.paycore.financialcore.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Composição do monólito modular: escaneia as entidades e repositórios de
 * accounts, ledger e payments em um único runtime Spring Boot (RFC-001 §7.2).
 */
@SpringBootApplication
@EntityScan(basePackages = "com.paycore.financialcore")
@EnableJpaRepositories(basePackages = "com.paycore.financialcore")
public class FinancialCoreApplication {

  public static void main(String[] args) {
    SpringApplication.run(FinancialCoreApplication.class, args);
  }
}
