package com.neit.personal_finance.services.impl;

import com.neit.personal_finance.documents.ActivityLog;
import com.neit.personal_finance.entities.Transaction;
import com.neit.personal_finance.repositories.mongo.ActivityLogRepository;
import com.neit.personal_finance.repositories.jpa.TransactionRepository;
import com.neit.personal_finance.services.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final ActivityLogRepository activityLogRepository;

    @Override
    public Transaction createTransaction(Transaction transaction, String userId) {
        Transaction saved = transactionRepository.save(transaction);

        // Log in MongoDB
        Map<String, Object> details = new HashMap<>();
        details.put("amount", transaction.getAmount());
        details.put("category", transaction.getCategory().getName());

        ActivityLog log = ActivityLog.builder()
                .userId(userId)
                .action("CREATE_TRANSACTION")
                .details(details)
                .build();

        activityLogRepository.save(log);
        return saved;
    }

    @Override
    public List<Transaction> getTransactions(Long userId) {
        return null;
//        return transactionRepository.findByUserId(Long.parseLong(userId));
    }

    @Override
    public List<Transaction> getTransactionsByDate(Long userId, LocalDate start, LocalDate end) {
        return null;
//        return transactionRepository.findByUserIdAndTransactionDateBetween(Long.parseLong(userId), start, end);
    }
}
