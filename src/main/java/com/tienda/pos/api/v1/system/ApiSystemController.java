package com.tienda.pos.api.v1.system;

import com.tienda.pos.TiendaPosApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ApiSystemController {

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "Tienda POS", "v1", serverVersion());
    }

    @GetMapping("/info")
    public InfoResponse info() {
        return new InfoResponse("Tienda POS", "v1", serverVersion(), List.of("bearer", "refresh-token"));
    }

    private String serverVersion() {
        String version = TiendaPosApplication.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "0.1.0" : version;
    }

    public record HealthResponse(String status, String application, String apiVersion, String serverVersion) {
    }

    public record InfoResponse(String application, String apiVersion, String serverVersion,
                               List<String> authentication) {
    }
}
