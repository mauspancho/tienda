package com.tienda.pos.api.v1.auth;

import com.tienda.pos.user.AppUser;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class ApiAuthModels {

    private ApiAuthModels() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record UserResponse(Long id, String username, String displayName, String email,
                               boolean enabled, List<String> roles) {
        public static UserResponse from(AppUser user) {
            List<String> roles = user.getRoles().stream()
                    .map(role -> role.getName().replaceFirst("^ROLE_", ""))
                    .sorted()
                    .toList();
            return new UserResponse(user.getId(), user.getUsername(), user.fullName(), user.getEmail(),
                    user.isActive(), roles);
        }
    }

    public record AuthResponse(String accessToken, String refreshToken, String tokenType,
                               long expiresIn, UserResponse user) {
    }
}
