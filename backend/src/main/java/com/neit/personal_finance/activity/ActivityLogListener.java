package com.neit.personal_finance.activity;

import com.neit.personal_finance.documents.ActivityLog;
import com.neit.personal_finance.repositories.mongo.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityLogListener {
    private final ActivityLogRepository repository;
    private final ThreadPoolTaskExecutor activityLogExecutor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void transactionCreated(TransactionCreated event) {
        try {
            activityLogExecutor.execute(() -> write(event));
        } catch (RejectedExecutionException ex) {
            log.warn("Activity log queue unavailable; skipped transaction {}", event.transactionId());
        }
    }

    private void write(TransactionCreated event) {
        try {
            repository.save(ActivityLog.builder()
                    .userId(Long.toString(event.userId()))
                    .action("CREATE_TRANSACTION")
                    .timestamp(event.timestamp())
                    .details(Map.of("transactionId", event.transactionId(), "amount", event.amount(),
                            "category", event.category()))
                    .build());
        } catch (RuntimeException ex) {
            // Financial data is already committed. Do not log payloads or database credentials.
            log.warn("Activity log failed for transaction {} ({})", event.transactionId(),
                    ex.getClass().getSimpleName());
        }
    }
}
