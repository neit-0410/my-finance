package com.neit.personal_finance.services.impl;

import com.neit.personal_finance.documents.ActivityLog;
import com.neit.personal_finance.entities.Transaction;
import com.neit.personal_finance.repositories.jpa.CategoryRepository;
import com.neit.personal_finance.sercurity.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
    private final CategoryRepository categoryRepository;
    private final CurrentUser currentUser;

    @Override
    public Transaction createTransaction(Transaction transaction) {
        var owner = currentUser.get();
        var category = categoryRepository.findById(transaction.getCategory().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (category.getUser() == null || category.getUser().getId() != owner.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        transaction.setId(null);
        transaction.setUser(owner);
        transaction.setCategory(category);
        Transaction saved = transactionRepository.save(transaction);

        // Log in MongoDB
        Map<String, Object> details = new HashMap<>();
        details.put("amount", transaction.getAmount());
        details.put("category", transaction.getCategory().getName());

        ActivityLog log = ActivityLog.builder()
                .userId(Long.toString(owner.getId()))
                .action("CREATE_TRANSACTION")
                .details(details)
                .build();

        activityLogRepository.save(log);
        return saved;
    }

    @Override
    public List<Transaction> getTransactions(Long userId) {
        currentUser.requireOwner(userId);
        return transactionRepository.findByUserId(userId);
    }

    @Override
    public List<Transaction> getTransactionsByDate(Long userId, LocalDate start, LocalDate end) {
        currentUser.requireOwner(userId);
        if (start.isAfter(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        return transactionRepository.findByUserIdAndTransactionDateBetween(userId, start, end);
    }
}
