package com.neit.personal_finance.services.impl;

import com.neit.personal_finance.dto.request.LoginRequest;
import com.neit.personal_finance.dto.request.RegisterRequest;
import com.neit.personal_finance.dto.response.AuthResponse;
import com.neit.personal_finance.entities.User;
import com.neit.personal_finance.repositories.jpa.UserRepository;
import com.neit.personal_finance.services.UserService;
import com.neit.personal_finance.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private PasswordEncoder passwordEncoder;


    @Override
    public AuthResponse register(RegisterRequest request) {
        if(userRepository.existsByUsername(request.getUsername())){
            throw new RuntimeException("Username already exists");
        }

        if(userRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                        .email(request.getEmail())
                                .password(passwordEncoder.encode(request.getPassword()))
                                        .role("ROLE_USER")
                                                .phone(request.getPhone())
                                                        .fullName(request.getFullName())
                                                                .build();

        userRepository.save(user);
        return AuthResponse.builder().message("User registered successfully").build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw new RuntimeException("Invalid password");
        }

        String token = jwtUtil.generateToken(user.getUsername());
        return AuthResponse.builder().message("Login successful").token(token).build();
    }

    @Override
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
