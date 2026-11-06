package com.paycore.financialcore.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Consultas de leitura sobre postings. O saldo é sempre uma projeção do
 * ledger, nunca um campo mutável na conta (RFC-001 §5, I-02).
 */
public interface PostingRepository extends JpaRepository<Posting, UUID> {

  /**
   * Saldo disponível em centavos: créditos menos débitos da conta. Contas sem
   * postings retornam zero.
   */
  @Query(
      value =
          "SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0) "
              + "FROM postings WHERE account_id = :accountId",
      nativeQuery = true)
  long balanceOf(@Param("accountId") UUID accountId);

  /** Primeira página, mais recente primeiro. */
  @Query(
      "select p from Posting p where p.accountId = :accountId "
          + "order by p.createdAt desc, p.id desc")
  List<Posting> findFirstPage(@Param("accountId") UUID accountId, Pageable pageable);

  /** Página seguinte (keyset), estritamente anterior ao cursor informado. */
  @Query(
      "select p from Posting p where p.accountId = :accountId "
          + "and (p.createdAt < :createdAt or (p.createdAt = :createdAt and p.id < :id)) "
          + "order by p.createdAt desc, p.id desc")
  List<Posting> findPageBefore(
      @Param("accountId") UUID accountId,
      @Param("createdAt") Instant createdAt,
      @Param("id") UUID id,
      Pageable pageable);
}
