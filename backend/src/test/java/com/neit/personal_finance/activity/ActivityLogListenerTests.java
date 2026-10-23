package com.neit.personal_finance.activity;

import com.neit.personal_finance.documents.ActivityLog;
import com.neit.personal_finance.repositories.mongo.ActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActivityLogListenerTests {
    @Configuration
    @EnableTransactionManagement
    static class EventsConfig {}

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {}
        protected void doRollback(DefaultTransactionStatus status) {}
    }

    @Test
    void dispatchesOnlyAfterCommitOnSeparateThreadAndNeverOnRollback() throws Exception {
        var repository = mock(ActivityLogRepository.class);
        var saved = new CountDownLatch(1);
        var worker = new AtomicReference<String>();
        when(repository.save(any(ActivityLog.class))).thenAnswer(call -> {
            worker.set(Thread.currentThread().getName());
            saved.countDown();
            return call.getArgument(0);
        });
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(EventsConfig.class, ActivityExecutorConfig.class, ActivityLogListener.class);
            context.registerBean(ActivityLogRepository.class, () -> repository);
            context.refresh();
            var tx = new TransactionTemplate(new TestTransactionManager());
            var event = new TransactionCreated(1L, 2L, 10.0, "Food", LocalDateTime.now());
            tx.executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(repository);
                status.setRollbackOnly();
            });
            verifyNoInteractions(repository);
            tx.executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(repository);
            });
            assertTrue(saved.await(5, TimeUnit.SECONDS));
            assertTrue(worker.get().startsWith("activity-log-"));
            verify(repository, times(1)).save(argThat(log -> log.getTimestamp().equals(event.timestamp())
                    && log.getUserId().equals("1") && log.getDetails().get("transactionId").equals(2L)));
        }
    }

    @Test
    void mongoFailureAndExecutorShutdownDoNotEscapeToCommittedCaller() throws Exception {
        var repository = mock(ActivityLogRepository.class);
        var executor = new ActivityExecutorConfig().activityLogExecutor();
        executor.initialize();
        try {
            var failed = new CountDownLatch(1);
            when(repository.save(any(ActivityLog.class))).thenAnswer(call -> {
                failed.countDown();
                throw new IllegalStateException("unavailable");
            });
            var listener = new ActivityLogListener(repository, executor);
            var event = new TransactionCreated(1L, 2L, 10.0, "Food", LocalDateTime.now());
            assertDoesNotThrow(() -> listener.transactionCreated(event));
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            executor.shutdown();
            assertDoesNotThrow(() -> listener.transactionCreated(event));
        } finally {
            executor.shutdown();
        }
    }
}
