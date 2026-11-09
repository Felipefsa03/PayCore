package com.paycore.financialcore.payments;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxReplayRepository extends JpaRepository<InboxReplay, UUID> {

  List<InboxReplay> findByInboxEventIdOrderByReplayedAtDesc(UUID inboxEventId);
}
