package com.neit.personal_finance.services;

import com.neit.personal_finance.entities.Transaction;

import java.time.LocalDate;
import java.util.List;

public interface TransactionService {
    Transaction createTransaction(Transaction transaction);
    List<Transaction> getTransactions(Long userId);
    List<Transaction> getTransactionsByDate(Long userId, LocalDate start, LocalDate end);
}
