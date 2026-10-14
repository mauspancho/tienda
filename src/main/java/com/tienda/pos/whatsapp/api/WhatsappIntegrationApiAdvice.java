package com.tienda.pos.whatsapp.api;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.common.NormalMode;
import com.tienda.pos.whatsapp.WppConnectException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes = WhatsappIntegrationApiController.class)
@NormalMode
public class WhatsappIntegrationApiAdvice {
    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String,String> domain(DomainException ex) { return Map.of("error", "INVALID_REQUEST", "message", ex.getMessage()); }
    @ExceptionHandler(WppConnectException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    Map<String,String> provider(WppConnectException ex) { return Map.of("error", "PROVIDER_ERROR", "message", ex.getMessage()); }
}
