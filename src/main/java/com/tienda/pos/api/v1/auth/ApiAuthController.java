package com.tienda.pos.api.v1.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.tienda.pos.api.v1.auth.ApiAuthModels.AuthResponse;
import static com.tienda.pos.api.v1.auth.ApiAuthModels.LoginRequest;
import static com.tienda.pos.api.v1.auth.ApiAuthModels.LogoutRequest;
import static com.tienda.pos.api.v1.auth.ApiAuthModels.RefreshRequest;
import static com.tienda.pos.api.v1.auth.ApiAuthModels.UserResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class ApiAuthController {

    private final ApiAuthService authService;

    public ApiAuthController(ApiAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.user(authentication.getName());
    }
}
