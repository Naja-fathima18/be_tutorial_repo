package com.app.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.app.backend.user.domain.Role;
import com.app.backend.user.domain.User;
import com.app.backend.user.persistence.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode(anyString())).willReturn("$2a$10$unknown");
        authService = new AuthService(userRepository, passwordEncoder, tokenService);
    }

    @Test
    void issuesTokenForMatchingCredentialsWithNormalizedEmail() {
        User user = new User("alice@example.com", "$2a$10$alice", Role.USER);
        IssuedToken token = new IssuedToken("jwt", Duration.ofMinutes(15));
        given(userRepository.findByEmail("alice@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "$2a$10$alice")).willReturn(true);
        given(tokenService.issue(user)).willReturn(token);

        IssuedToken issued = authService.login(" Alice@Example.com", "password123");

        assertThat(issued).isSameAs(token);
    }

    @Test
    void rejectsWrongPassword() {
        User user = new User("alice@example.com", "$2a$10$alice", Role.USER);
        given(userRepository.findByEmail("alice@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "$2a$10$alice")).willReturn(false);

        assertThatThrownBy(() -> authService.login("alice@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        then(tokenService).should(never()).issue(any());
    }

    @Test
    void rejectsUnknownEmailWithSameMessageAfterComparingAgainstPlaceholderHash() {
        given(userRepository.findByEmail("ghost@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost@example.com", "password123"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        then(passwordEncoder).should().matches(eq("password123"), eq("$2a$10$unknown"));
        then(tokenService).should(never()).issue(any());
    }

    @Test
    void rejectsPasswordBeyondBcryptLimitAsInvalidCredentials() {
        String tooLong = "é".repeat(40);

        assertThatThrownBy(() -> authService.login("alice@example.com", tooLong))
                .isInstanceOf(InvalidCredentialsException.class);
        then(userRepository).should(never()).findByEmail(any());
    }
}
