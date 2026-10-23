package com.neit.personal_finance.activity;

import java.time.LocalDateTime;

public record TransactionCreated(long userId, long transactionId, Double amount,
                                 String category, LocalDateTime timestamp) {}
