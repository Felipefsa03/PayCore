package com.paycore.financialcore.ledger;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Registro de diário: conjunto atômico e balanceado de postings que
 * materializa uma transação contábil (RFC-001 §5). Imutável após criação —
 * nunca há updatedAt (invariante I-03). O balanceamento (débitos = créditos
 * por moeda) é responsabilidade do serviço de aplicação do card dedicado ao
 * ledger de partidas dobradas, não desta entidade de persistência.
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "transfer_id", nullable = false, updatable = false, unique = true)
  private UUID transferId;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.PERSIST, orphanRemoval = false)
  private List<Posting> postings = new ArrayList<>();

  protected JournalEntry() {
    // required by JPA
  }

  public JournalEntry(UUID id, UUID transferId, String currency) {
    this.id = id;
    this.transferId = transferId;
    this.currency = currency;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTransferId() {
    return transferId;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<Posting> getPostings() {
    return postings;
  }
}
