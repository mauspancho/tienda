package com.tienda.pos.whatsapp.automation;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.exception.DomainException;
import com.tienda.pos.whatsapp.WppConnectException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes = WhatsappAutomationApiController.class)
@NormalMode
public class WhatsappAutomationApiAdvice {
    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String,String> domain(DomainException ex) { return Map.of("error", "STORE_NOT_ELIGIBLE", "message", ex.getMessage()); }
    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String,String> invalid(IllegalArgumentException ex) { return Map.of("error", "INVALID_REQUEST", "message", ex.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    Map<String,String> forbidden(AccessDeniedException ex) { return Map.of("error", "FORBIDDEN", "message", ex.getMessage()); }
    @ExceptionHandler(AutomationRateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    Map<String,String> rateLimited(AutomationRateLimitException ex) { return Map.of("error", "RATE_LIMITED", "message", ex.getMessage()); }
    @ExceptionHandler(WppConnectException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    Map<String,String> provider(WppConnectException ex) { return Map.of("error", "PROVIDER_ERROR", "message", ex.getMessage()); }
}
