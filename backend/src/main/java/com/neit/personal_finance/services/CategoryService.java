package com.neit.personal_finance.services;

import com.neit.personal_finance.entities.Category;

import java.util.List;

public interface CategoryService {
    Category createCategory(Category category);

    List<Category> getUserCategories(Long userId);
}