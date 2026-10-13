package com.neit.personal_finance.services.impl;


import com.neit.personal_finance.entities.Category;
import com.neit.personal_finance.sercurity.CurrentUser;
import com.neit.personal_finance.repositories.jpa.CategoryRepository;
import com.neit.personal_finance.services.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUser currentUser;

    @Override
    public Category createCategory(Category category) {
        category.setId(null);
        category.setUser(currentUser.get());
        return categoryRepository.save(category);
    }

    @Override
    public List<Category> getUserCategories(Long userId) {
        currentUser.requireOwner(userId);
        return categoryRepository.findByUserId(userId);
    }
}
