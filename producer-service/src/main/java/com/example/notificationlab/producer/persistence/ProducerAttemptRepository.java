package com.example.notificationlab.producer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProducerAttemptRepository extends JpaRepository<ProducerAttemptEntity, Long> {

    List<ProducerAttemptEntity> findByNotificationIdOrderByAttemptNumberAsc(String notificationId);
}
