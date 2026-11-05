package com.paycore.financialcore.shared;

/**
 * Máquina de estados finita da transferência (docs/architecture/
 * modelo-dominio-estados-transferencia.md). REJECTED e FAILED são terminais
 * e mutuamente exclusivos de COMPLETED.
 */
public enum TransferState {
  RECEIVED,
  VALIDATING,
  PROCESSING,
  COMPLETED,
  REJECTED,
  FAILED
}
