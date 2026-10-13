package com.neit.personal_finance.repositories.jpa;


import com.neit.personal_finance.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserId(Long userId);

    List<Transaction> findByUserIdAndTransactionDateBetween(
            Long userId,
            LocalDate start,
            LocalDate end
    );
}