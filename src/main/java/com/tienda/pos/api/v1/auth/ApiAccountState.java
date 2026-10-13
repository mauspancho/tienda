package com.tienda.pos.api.v1.auth;

import com.tienda.pos.user.AppUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ApiAccountState {
    private ApiAccountState() {}

    public static boolean allowed(AppUser user) {
        return user.isActive() && user.getTenant() != null && user.getTenant().isActive()
                && !user.hasRole("ROLE_PLATFORM_ADMIN");
    }

    // Bind tokens to credentials, privileges and suspension history, not last-login updates.
    public static String stamp(AppUser user) {
        String state = user.getId() + ":" + user.getUsername() + ":" + user.getPasswordHash()
                + ":" + user.getAuthEpoch() + ":" + user.getTenant().getId() + ":" + user.getTenant().getAuthEpoch()
                + ":" + user.getRoles().stream().map(r -> r.getName()).sorted().toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
