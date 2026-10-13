package com.neit.personal_finance.services;


import com.neit.personal_finance.dto.request.LoginRequest;
import com.neit.personal_finance.dto.request.RegisterRequest;
import com.neit.personal_finance.dto.response.AuthResponse;
import com.neit.personal_finance.entities.User;

public interface UserService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    User getById(Long id);
}