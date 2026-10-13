package com.neit.personal_finance.dto.request;

import com.neit.personal_finance.entities.Transaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TransactionRequest(@NotNull @Positive Double amount,
                                 @NotNull Transaction.Type type,
                                 @NotNull @Positive Long categoryId,
                                 @Size(max = 255) String description,
                                 @NotNull LocalDate transactionDate) {}
