package com.tienda.pos.whatsapp.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlatformAutomationTokenRepository extends JpaRepository<PlatformAutomationToken, Long> {
    Optional<PlatformAutomationToken> findByTokenHashAndActiveTrue(String tokenHash);
    List<PlatformAutomationToken> findAllByOrderByCreatedAtDesc();
}
