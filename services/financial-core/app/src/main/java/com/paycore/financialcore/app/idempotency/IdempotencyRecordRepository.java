package com.paycore.financialcore.app.idempotency;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

  Optional<IdempotencyRecord> findByOwnerIdAndOperationAndIdempotencyKey(
      UUID ownerId, String operation, String idempotencyKey);
}
