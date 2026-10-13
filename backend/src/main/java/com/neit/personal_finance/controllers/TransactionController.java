package com.neit.personal_finance.controllers;

import com.neit.personal_finance.entities.Transaction;
import com.neit.personal_finance.services.TransactionService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction,
                                                         @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(transactionService.createTransaction(transaction, userId));
    }

//    @GetMapping("/user/{userId}")
//    public ResponseEntity<List<Transaction>> getUserTransactions(@PathVariable String userId) {
//        return ResponseEntity.ok(transactionService.getTransactions(userId));
//    }

    @GetMapping("/user/{userId}/range")
    public ResponseEntity<List<Transaction>> getTransactionsByDate(@PathVariable String userId,
                                                                   @RequestParam String start,
                                                                   @RequestParam String end) {
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        return ResponseEntity.ok(transactionService.getTransactionsByDate(Long.parseLong(userId), startDate, endDate));
    }
}