package com.neit.personal_finance.sercurity;

import com.neit.personal_finance.entities.User;
import com.neit.personal_finance.repositories.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final UserRepository users;

    public User get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public User lock() {
        return users.lockById(get().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public User requireOwner(Long userId) {
        User owner = get();
        if (userId == null || owner.getId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return owner;
    }
}
