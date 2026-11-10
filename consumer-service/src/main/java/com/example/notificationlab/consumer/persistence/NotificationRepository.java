package com.example.notificationlab.consumer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    Optional<NotificationEntity> findByMessageId(String messageId);
}
