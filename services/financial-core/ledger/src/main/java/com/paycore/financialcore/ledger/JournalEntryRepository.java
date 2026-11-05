package com.paycore.financialcore.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

  Optional<JournalEntry> findByTransferId(UUID transferId);
}
