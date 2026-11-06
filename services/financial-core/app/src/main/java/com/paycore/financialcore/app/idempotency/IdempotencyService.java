package com.paycore.financialcore.app.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

  private final IdempotencyRecordRepository records;

  public IdempotencyService(IdempotencyRecordRepository records) {
    this.records = records;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IdempotencyRecord begin(
      UUID ownerId, String operation, String key, String fingerprint, Instant expiresAt) {
    Optional<IdempotencyRecord> existing =
        records.findByOwnerIdAndOperationAndIdempotencyKey(ownerId, operation, key);
    if (existing.isPresent()) {
      IdempotencyRecord current = existing.get();
      if (!current.getFingerprint().equals(fingerprint)) {
        throw new IdempotencyKeyConflictException();
      }
      if (IdempotencyRecord.Status.PROCESSING.name().equals(current.getStatus())) {
        throw new IdempotencyKeyInProgressException();
      }
      return current; // replay
    }

    try {
      IdempotencyRecord record =
          new IdempotencyRecord(UUID.randomUUID(), ownerId, operation, key, fingerprint, expiresAt);
      return records.saveAndFlush(record);
    } catch (Exception exception) {
      return fetchExisting(ownerId, operation, key, fingerprint);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IdempotencyRecord fetchExisting(
      UUID ownerId, String operation, String key, String fingerprint) {
    IdempotencyRecord existing =
        records
            .findByOwnerIdAndOperationAndIdempotencyKey(ownerId, operation, key)
            .orElseThrow(() -> new IllegalStateException("idempotency record not found after conflict"));
    if (!existing.getFingerprint().equals(fingerprint)) {
      throw new IdempotencyKeyConflictException();
    }
    if (IdempotencyRecord.Status.PROCESSING.name().equals(existing.getStatus())) {
      throw new IdempotencyKeyInProgressException();
    }
    return existing;
  }

  @Transactional
  public void complete(IdempotencyRecord record, UUID transferId, String responseSnapshot) {
    IdempotencyRecord current =
        records.findById(record.getId()).orElseThrow(() -> new IllegalStateException("idempotency record not found"));
    current.complete(transferId, responseSnapshot);
    records.save(current);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void release(IdempotencyRecord record) {
    records.findById(record.getId()).ifPresent(records::delete);
  }
}
