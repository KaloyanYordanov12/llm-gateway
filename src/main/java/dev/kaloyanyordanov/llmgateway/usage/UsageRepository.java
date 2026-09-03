package dev.kaloyanyordanov.llmgateway.usage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link UsageRecord}s.
 */
public interface UsageRepository extends JpaRepository<UsageRecord, Long> {

    /**
     * @param clientId the client
     * @return that client's usage records
     */
    List<UsageRecord> findByClientId(Long clientId);

    /**
     * @param clientId the client
     * @return that client's usage records, newest first
     */
    List<UsageRecord> findByClientIdOrderByCreatedAtDesc(Long clientId);
}
