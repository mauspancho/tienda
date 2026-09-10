package com.tienda.pos.security;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.user.AppUserRepository;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

@Service
@NormalMode
public class SessionRevocationService {
    private final SessionRegistry registry;
    private final AppUserRepository users;

    public SessionRevocationService(SessionRegistry registry, AppUserRepository users) {
        this.registry = registry;
        this.users = users;
    }

    public void expireUserSessions(String username) {
        afterCommit(() -> expire(Set.of(username), false));
    }

    public void expireTenantSessions(Long tenantId) {
        Set<String> usernames = Set.copyOf(users.findNonPlatformUsernamesByTenantId(tenantId));
        afterCommit(() -> expire(usernames, true));
    }

    private void expire(Set<String> usernames, boolean excludePlatform) {
        for (Object principal : registry.getAllPrincipals()) {
            String username = principal instanceof UserDetails user ? user.getUsername() : principal.toString();
            boolean platform = principal instanceof UserDetails user && user.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
            if (usernames.contains(username) && !(excludePlatform && platform)) {
                registry.getAllSessions(principal, false).forEach(session -> session.expireNow());
            }
        }
    }

    private void afterCommit(Runnable action) {
        // A failed/rolled-back suspension must not log out otherwise valid sessions.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }
}
