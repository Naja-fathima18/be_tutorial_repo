package com.app.backend.user.service;

import com.app.backend.common.error.BadRequestException;
import com.app.backend.common.error.ConflictException;
import com.app.backend.common.error.NotFoundException;
import com.app.backend.user.domain.Role;
import com.app.backend.user.domain.User;
import com.app.backend.user.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static boolean exceedsBcryptLimit(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    @Transactional
    public User register(String email, String rawPassword) {
        return create(normalizeEmail(email), rawPassword, Role.USER);
    }

    @Transactional
    public boolean createAdminIfAbsent(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            return false;
        }
        create(normalizedEmail, rawPassword, Role.ADMIN);
        return true;
    }

    @Transactional(readOnly = true)
    public User getById(long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    private User create(String normalizedEmail, String rawPassword, Role role) {
        requireAcceptablePassword(rawPassword);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw emailTaken();
        }
        try {
            return userRepository.saveAndFlush(new User(normalizedEmail, passwordEncoder.encode(rawPassword), role));
        } catch (DataIntegrityViolationException duplicate) {
            throw emailTaken();
        }
    }

    private static void requireAcceptablePassword(String rawPassword) {
        if (rawPassword.length() < MIN_PASSWORD_LENGTH || exceedsBcryptLimit(rawPassword)) {
            throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH
                    + " characters and at most " + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    private static ConflictException emailTaken() {
        return new ConflictException("Email is already registered");
    }
}
