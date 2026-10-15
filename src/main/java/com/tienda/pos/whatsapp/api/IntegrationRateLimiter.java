package com.tienda.pos.whatsapp.api;

import org.springframework.http.HttpStatus;
import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@NormalMode
public class IntegrationRateLimiter {
    private static final int LIMIT = 60;
    private record Window(long minute, int count) { }
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();
    public void check(Long tokenId) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window current = windows.compute(tokenId, (id, old) -> old == null || old.minute != minute ? new Window(minute, 1) : new Window(minute, old.count + 1));
        if (current.count > LIMIT) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Límite de 60 solicitudes por minuto excedido.");
    }
}
