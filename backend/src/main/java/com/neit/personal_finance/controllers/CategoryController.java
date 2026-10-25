package com.neit.personal_finance.controllers;

import com.neit.personal_finance.idempotency.IdempotencyService;

import com.neit.personal_finance.entities.Category;
import com.neit.personal_finance.dto.request.CategoryRequest;
import jakarta.validation.Valid;
import com.neit.personal_finance.services.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final IdempotencyService idempotency;

    @PostMapping
    public ResponseEntity<String> create(@Valid @RequestBody CategoryRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return idempotency.execute(key, "POST /api/categories", request, () ->
                categoryService.createCategory(Category.builder().name(request.name()).type(request.type()).build()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Category>> getUserCategories(@PathVariable Long userId) {
        return ResponseEntity.ok(categoryService.getUserCategories(userId));
    }
}
