package com.app.backend.user.service;

import com.app.backend.user.security.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final UserService userService;

    public AdminBootstrap(AdminProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.info("Admin bootstrap skipped: ADMIN_EMAIL and ADMIN_PASSWORD are not both set");
            return;
        }
        if (userService.createAdminIfAbsent(properties.email(), properties.password())) {
            log.info("Admin bootstrap user created");
        } else {
            log.info("Admin bootstrap skipped: a user with the configured email already exists");
        }
    }
}
