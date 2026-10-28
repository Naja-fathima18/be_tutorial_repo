package com.app.backend.user.service;

import com.app.backend.user.domain.User;
import com.app.backend.user.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final String unknownUserHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public IssuedToken login(String email, String rawPassword) {
        if (UserService.exceedsBcryptLimit(rawPassword)) {
            throw new InvalidCredentialsException();
        }
        Optional<User> user = userRepository.findByEmail(UserService.normalizeEmail(email));
        String hash = user.map(User::getPasswordHash).orElse(unknownUserHash);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hash);
        return user.filter(found -> passwordMatches)
                .map(tokenService::issue)
                .orElseThrow(InvalidCredentialsException::new);
    }
}
