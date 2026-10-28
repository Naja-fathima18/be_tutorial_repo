package com.app.backend.user.api;

import com.app.backend.user.domain.Role;
import com.app.backend.user.domain.User;

public record UserResponse(Long id, String email, Role role) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
