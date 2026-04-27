package com.miniurl.url.repository;

import com.miniurl.url.entity.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {
    java.util.List<Outbox> findByProcessedFalseOrderByCreatedAtAsc();
}
