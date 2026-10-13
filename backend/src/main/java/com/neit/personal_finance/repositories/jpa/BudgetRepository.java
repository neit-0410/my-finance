package com.neit.personal_finance.repositories.jpa;


import com.neit.personal_finance.entities.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndMonthAndYear(
            Long userId,
            int month,
            int year
    );
}
