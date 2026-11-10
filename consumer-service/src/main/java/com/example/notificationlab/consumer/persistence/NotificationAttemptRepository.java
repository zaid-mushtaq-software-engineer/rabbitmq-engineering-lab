package com.example.notificationlab.consumer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttemptEntity, Long> {

    List<NotificationAttemptEntity> findByNotificationIdOrderByAttemptNumberAsc(String notificationId);
}
