package com.neit.personal_finance.dto.request;

import com.neit.personal_finance.entities.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(@NotBlank @Size(max = 255) String name, @NotNull Category.Type type) {}
