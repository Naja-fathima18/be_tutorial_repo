package com.app.backend.user.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.app.backend.user.security.AdminProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserService userService;

    @Test
    void createsAdminWhenEmailAndPasswordAreConfigured() {
        AdminBootstrap bootstrap =
                new AdminBootstrap(new AdminProperties("admin@example.com", "adminpass1"), userService);

        bootstrap.run(new DefaultApplicationArguments());

        then(userService).should().createAdminIfAbsent("admin@example.com", "adminpass1");
    }

    @Test
    void skipsWhenPasswordIsMissing() {
        AdminBootstrap bootstrap = new AdminBootstrap(new AdminProperties("admin@example.com", ""), userService);

        bootstrap.run(new DefaultApplicationArguments());

        then(userService).should(never()).createAdminIfAbsent(any(), any());
    }

    @Test
    void skipsWhenEmailIsMissing() {
        AdminBootstrap bootstrap = new AdminBootstrap(new AdminProperties(null, "adminpass1"), userService);

        bootstrap.run(new DefaultApplicationArguments());

        then(userService).should(never()).createAdminIfAbsent(any(), any());
    }
}
