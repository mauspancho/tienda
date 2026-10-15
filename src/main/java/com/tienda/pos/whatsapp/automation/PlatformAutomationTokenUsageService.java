package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@NormalMode
public class PlatformAutomationTokenUsageService {
    static final Duration UPDATE_INTERVAL = Duration.ofMinutes(1);

    private final PlatformAutomationTokenRepository tokens;

    public PlatformAutomationTokenUsageService(PlatformAutomationTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long tokenId, LocalDateTime usedAt) {
        tokens.touchLastUsedAt(tokenId, usedAt, usedAt.minus(UPDATE_INTERVAL));
    }
}
