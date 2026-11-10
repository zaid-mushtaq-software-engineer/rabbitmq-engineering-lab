package com.example.notificationlab.producer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProducerNotificationRepository extends JpaRepository<ProducerNotificationEntity, String>,
        JpaSpecificationExecutor<ProducerNotificationEntity> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProducerNotificationEntity n
            SET n.status = com.example.notificationlab.producer.persistence.ProducerNotificationStatus.QUEUED,
                n.queuedAt = :queuedAt,
                n.updatedAt = :updatedAt
            WHERE n.id = :id AND n.status = com.example.notificationlab.producer.persistence.ProducerNotificationStatus.PENDING_PUBLISH
            """)
    int markQueuedIfPendingPublish(
            @Param("id") String id,
            @Param("queuedAt") Instant queuedAt,
            @Param("updatedAt") Instant updatedAt
    );
}
