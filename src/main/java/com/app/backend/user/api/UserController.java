package com.app.backend.user.api;

import com.app.backend.common.api.PageResponse;
import com.app.backend.user.service.UserService;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.getById(Long.parseLong(jwt.getSubject())));
    }

    @GetMapping
    public PageResponse<UserResponse> list(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.from(userService.list(pageable).map(UserResponse::from));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable @Positive long id) {
        return UserResponse.from(userService.getById(id));
    }
}
