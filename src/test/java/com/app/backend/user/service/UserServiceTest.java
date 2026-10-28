package com.app.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.ConflictException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.user.domain.Role;
import com.app.backend.user.domain.User;
import com.app.backend.user.persistence.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registersUserWithNormalizedEmailHashedPasswordAndUserRole() {
        given(userRepository.existsByEmail("alice@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$2a$10$hash");
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User registered = userService.register("  Alice@Example.COM ", "password123");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(registered).isSameAs(saved.getValue());
    }

    @Test
    void rejectsRegistrationWhenEmailIsAlreadyTaken() {
        given(userRepository.existsByEmail("alice@example.com")).willReturn(true);

        assertThatThrownBy(() -> userService.register("ALICE@example.com", "password123"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email is already registered");
        then(userRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void mapsUniqueConstraintRaceToConflict() {
        given(userRepository.existsByEmail("alice@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$2a$10$hash");
        given(userRepository.saveAndFlush(any(User.class)))
                .willThrow(new DataIntegrityViolationException("uk_app_users_email"));

        assertThatThrownBy(() -> userService.register("alice@example.com", "password123"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsPasswordLongerThanSeventyTwoBytes() {
        String multiByte = "é".repeat(40);

        assertThatThrownBy(() -> userService.register("alice@example.com", multiByte))
                .isInstanceOf(BadRequestException.class);
        then(passwordEncoder).should(never()).encode(any());
    }

    @Test
    void rejectsPasswordShorterThanEightCharacters() {
        assertThatThrownBy(() -> userService.register("alice@example.com", "short"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createsAdminWhenEmailIsFree() {
        given(userRepository.existsByEmail("admin@example.com")).willReturn(false);
        given(passwordEncoder.encode("adminpass1")).willReturn("$2a$10$admin");
        given(userRepository.saveAndFlush(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        boolean created = userService.createAdminIfAbsent("Admin@Example.com", "adminpass1");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().saveAndFlush(saved.capture());
        assertThat(created).isTrue();
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
    }

    @Test
    void leavesExistingAccountUntouchedWhenAdminEmailExists() {
        given(userRepository.existsByEmail("admin@example.com")).willReturn(true);

        boolean created = userService.createAdminIfAbsent("admin@example.com", "adminpass1");

        assertThat(created).isFalse();
        then(userRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void throwsNotFoundForUnknownUserId() {
        given(userRepository.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User 42 not found");
    }
}
