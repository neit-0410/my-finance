package com.neit.personal_finance.controllers;

import com.neit.personal_finance.dto.request.TransactionRequest;
import com.neit.personal_finance.entities.Transaction;
import com.neit.personal_finance.entities.Category;
import com.neit.personal_finance.services.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
        var category = new Category();
        category.setId(request.categoryId());
        var transaction = Transaction.builder().amount(request.amount()).type(request.type())
                .category(category).description(request.description()).transactionDate(request.transactionDate()).build();
        return ResponseEntity.ok(transactionService.createTransaction(transaction));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Transaction>> getUserTransactions(@PathVariable Long userId) {
        return ResponseEntity.ok(transactionService.getTransactions(userId));
    }

    @GetMapping("/user/{userId}/range")
    public ResponseEntity<List<Transaction>> getTransactionsByDate(@PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(transactionService.getTransactionsByDate(userId, start, end));
    }
}
