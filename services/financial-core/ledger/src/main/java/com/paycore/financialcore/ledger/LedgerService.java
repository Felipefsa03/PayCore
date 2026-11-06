package com.paycore.financialcore.ledger;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do ledger de partidas dobradas (RF-04, I-01, I-02, I-03).
 *
 * <p>Um {@link JournalEntry} só é persistido quando a soma dos créditos é igual à soma dos
 * débitos por moeda. A validação é feita aqui e reforçada por triggers no banco, de forma
 * que nem a aplicação nem uma alteração direta consigam persistir um lançamento
 * desbalanceado.
 *
 * <p>Lançamentos efetivados são imutáveis: correções entram como um novo journal entry
 * compensatório, preservando o histórico original (I-03).
 */
@Service
public class LedgerService {

  private final JournalEntryRepository journals;
  private final PostingRepository postings;
  @PersistenceContext
  private EntityManager entityManager;

  public LedgerService(JournalEntryRepository journals, PostingRepository postings) {
    this.journals = journals;
    this.postings = postings;
  }

  /**
   * Registra uma transferência concluída como um journal entry balanceado, com um débito na
   * conta de origem e um crédito na conta de destino.
   *
   * @throws UnbalancedJournalEntryException se a soma dos lançamentos não for zero
   * @throws IllegalArgumentException se os parâmetros forem inválidos
   */
  @Transactional
  public JournalEntry record(
      UUID transferId, UUID sourceAccountId, UUID destinationAccountId, long amount, String currency) {
    if (transferId == null) {
      throw new IllegalArgumentException("transferId must not be null");
    }
    if (sourceAccountId == null || destinationAccountId == null) {
      throw new IllegalArgumentException("account ids must not be null");
    }
    if (sourceAccountId.equals(destinationAccountId)) {
      throw new IllegalArgumentException("source and destination accounts must differ");
    }
    if (amount <= 0) {
      throw new IllegalArgumentException("amount must be greater than zero");
    }
    if (currency == null || currency.length() != 3) {
      throw new IllegalArgumentException("currency must be a 3-letter code");
    }
    if (journals.findByTransferId(transferId).isPresent()) {
      throw new IllegalStateException("a journal entry already exists for transfer " + transferId);
    }

    JournalEntry entry = new JournalEntry(UUID.randomUUID(), transferId, currency);
    entry.getPostings()
        .add(
            new Posting(
                UUID.randomUUID(), entry, sourceAccountId, Posting.Direction.DEBIT, amount, currency));
    entry.getPostings()
        .add(
            new Posting(
                UUID.randomUUID(), entry, destinationAccountId, Posting.Direction.CREDIT, amount, currency));

    validateBalanced(entry);
    entityManager.persist(entry);
    return entry;
  }

  /**
   * Cria um lançamento compensatório que reverte o efeito de uma transferência concluída,
   * sem alterar o lançamento original (I-03).
   */
  @Transactional
  public JournalEntry reverse(
      UUID originalTransferId,
      UUID reversalTransferId,
      UUID sourceAccountId,
      UUID destinationAccountId,
      long amount,
      String currency) {
    if (originalTransferId == null || reversalTransferId == null) {
      throw new IllegalArgumentException("transfer ids must not be null");
    }
    if (originalTransferId.equals(reversalTransferId)) {
      throw new IllegalArgumentException("reversal transfer id must differ from the original");
    }
    if (!journals.findByTransferId(originalTransferId).isPresent()) {
      throw new IllegalArgumentException("original transfer has no journal entry: " + originalTransferId);
    }
    // Sentidos invertidos: credita a origem e debita o destino.
    return record(reversalTransferId, destinationAccountId, sourceAccountId, amount, currency);
  }

  @Transactional(readOnly = true)
  public long balanceOf(UUID accountId) {
    if (accountId == null) {
      throw new IllegalArgumentException("accountId must not be null");
    }
    return postings.balanceOf(accountId);
  }

  @Transactional(readOnly = true)
  public java.util.Optional<JournalEntry> findByTransferId(UUID transferId) {
    return journals.findByTransferId(transferId);
  }

  private static void validateBalanced(JournalEntry entry) {
    long net =
        entry.getPostings().stream()
            .mapToLong(
                posting ->
                    posting.getDirection() == Posting.Direction.CREDIT
                        ? posting.getAmount()
                        : -posting.getAmount())
            .sum();
    if (net != 0) {
      throw new UnbalancedJournalEntryException(entry.getId(), net);
    }
    for (Posting posting : entry.getPostings()) {
      if (!posting.getCurrency().equals(entry.getCurrency())) {
        throw new IllegalArgumentException("posting currency must match journal entry currency");
      }
    }
  }
}
