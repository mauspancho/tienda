package com.tienda.pos.api.v1.whatsapp;

import com.tienda.pos.common.NormalMode;
import com.tienda.pos.api.v1.error.ApiErrorResponse;
import com.tienda.pos.whatsapp.WppConnectException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@NormalMode
@Order(-2)
@RestControllerAdvice(assignableTypes = ApiWhatsappController.class)
public class ApiWhatsappAdvice {
    @ExceptionHandler(WppConnectException.class)
    ResponseEntity<ApiErrorResponse> provider(WppConnectException ex) {
        return ResponseEntity.status(502).body(ApiErrorResponse.of(502, "WHATSAPP_PROVIDER_UNAVAILABLE",
                "El proveedor de WhatsApp no está disponible. Actualiza la conexión e intenta de nuevo."));
    }
}
