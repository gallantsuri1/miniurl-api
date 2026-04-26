package com.miniurl.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniurl.identity.entity.Outbox;
import com.miniurl.identity.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        List<Outbox> pendingEvents = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc();
        
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events", pendingEvents.size());

        for (Outbox event : pendingEvents) {
            try {
                // Determine topic based on aggregateType
                String topic = determineTopic(event.getAggregateType());
                
                // Deserialize payload back to object for Kafka JSON serializer
                Object payload = objectMapper.readValue(event.getPayload(), Object.class);
                
                kafkaTemplate.send(topic, event.getAggregateId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.debug("Successfully published event {} to topic {}", event.getType(), topic);
                        } else {
                            log.error("Failed to publish event {}: {}", event.getType(), ex.getMessage());
                        }
                    });

                event.setProcessed(true);
                event.setProcessedAt(LocalDateTime.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Error processing outbox event {}: {}", event.getId(), e.getMessage());
                // We don't mark as processed, so it will be retried
            }
        }
    }

    private String determineTopic(String aggregateType) {
        return switch (aggregateType) {
            case "USER" -> "notifications";
            default -> "general-events";
        };
    }
}
