package com.neit.personal_finance.services.impl;


import com.neit.personal_finance.entities.Category;
import com.neit.personal_finance.cache.UserReadCache;
import tools.jackson.core.type.TypeReference;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
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
    private final UserReadCache cache;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Category createCategory(Category category) {
        category.setId(null);
        category.setUser(currentUser.lock());
        Category saved = categoryRepository.save(category);
        cache.invalidate(category.getUser().getId());
        return saved;
    }

    @Override
    public List<Category> getUserCategories(Long userId) {
        var owner = currentUser.requireOwner(userId);
        return cache.get(owner, "categories", new TypeReference<List<Category>>() {},
                () -> categoryRepository.findByUserId(userId));
    }
}
