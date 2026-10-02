package com.tienda.pos.api.v1.auth;

import com.tienda.pos.api.v1.error.ApiAuthenticationException;
import com.tienda.pos.api.v1.error.ApiNotFoundException;
import com.tienda.pos.user.AppUser;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.tienda.pos.api.v1.auth.ApiAuthModels.AuthResponse;
import static com.tienda.pos.api.v1.auth.ApiAuthModels.UserResponse;

@Service
public class ApiAuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final ApiTokenService tokenService;
    private final ApiRefreshTokenService refreshTokenService;

    public ApiAuthService(AuthenticationManager authenticationManager, AppUserRepository userRepository,
                          ApiTokenService tokenService, ApiRefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse login(String username, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException ex) {
            throw new ApiAuthenticationException("Usuario o contraseña incorrectos.");
        }
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiAuthenticationException("Usuario o contraseña incorrectos."));
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        return response(user, refreshTokenService.issue(user).rawToken());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        ApiRefreshTokenService.IssuedRefreshToken issued = refreshTokenService.rotate(refreshToken);
        return response(issued.user(), issued.rawToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse user(String username) {
        return userRepository.findByUsername(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiNotFoundException("Usuario no encontrado."));
    }

    private AuthResponse response(AppUser user, String refreshToken) {
        return new AuthResponse(tokenService.createAccessToken(user), refreshToken, "Bearer",
                tokenService.expiresInSeconds(), UserResponse.from(user));
    }
}
