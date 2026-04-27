package com.miniurl.url.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniurl.url.entity.Outbox;
import com.miniurl.url.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        List<Outbox> pendingEvents = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc();
        
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Processing {} pending outbox events in URL service", pendingEvents.size());

        for (Outbox event : pendingEvents) {
            try {
                String topic = determineTopic(event.getAggregateType());
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
            }
        }
    }

    private String determineTopic(String aggregateType) {
        return switch (aggregateType) {
            case "URL" -> "url-events";
            default -> "general-events";
        };
    }
}
