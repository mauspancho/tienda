package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@NormalMode
public class AutomationRateLimiter {
    private record Window(long minute, int count) { }
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();
    public void check(Long tokenId) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window value = windows.compute(tokenId, (key, old) -> old == null || old.minute != minute
                ? new Window(minute, 1) : new Window(minute, old.count + 1));
        if (value.count > 120) throw new AutomationRateLimitException("Límite de solicitudes de automatización excedido.");
    }
}
