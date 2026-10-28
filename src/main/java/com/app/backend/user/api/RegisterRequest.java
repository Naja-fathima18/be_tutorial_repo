package com.app.backend.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 8, max = 72) String password) {

    @Override
    public String toString() {
        return "RegisterRequest[email=****, password=****]";
    }
}
