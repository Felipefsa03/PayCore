package com.paycore.financialcore.app.outbox;

import com.paycore.financialcore.payments.OutboxEvent;

/**
 * Porta de saída do outbox para o broker de eventos.
 *
 * <p>O card do outbox entrega o contrato e a implementação de laboratório
 * (log); a integração real com Kafka entra no card de tópicos versionados. A
 * porta permite trocar o transporte sem alterar a transação do pagamento nem o
 * publicador.
 */
public interface DomainEventPublisher {

  /**
   * Publica um evento. Lançar exceção sinaliza falha transitória: o publicador
   * mantém a linha pendente e agenda nova tentativa com backoff.
   *
   * @param event evento reservado pelo publicador
   */
  void publish(OutboxEvent event);
}
