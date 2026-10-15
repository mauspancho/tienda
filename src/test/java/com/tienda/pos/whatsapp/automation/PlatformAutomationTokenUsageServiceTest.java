package com.tienda.pos.whatsapp.automation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlatformAutomationTokenUsageServiceTest {
    @Test void updatesOnlyLastUsedAtWithFrequencyCutoff() {
        PlatformAutomationTokenRepository repository = mock(PlatformAutomationTokenRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 10, 13, 21, 0);

        new PlatformAutomationTokenUsageService(repository).record(5L, now);

        verify(repository).touchLastUsedAt(5L, now, now.minus(PlatformAutomationTokenUsageService.UPDATE_INTERVAL));
    }
}
