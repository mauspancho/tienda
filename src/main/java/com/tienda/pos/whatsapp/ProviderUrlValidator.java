package com.tienda.pos.whatsapp;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
@NormalMode
public class ProviderUrlValidator {
    private final WhatsappProperties properties;
    public ProviderUrlValidator(WhatsappProperties properties) { this.properties = properties; }

    public URI validate(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            boolean validScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            boolean clean = uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && (uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath()));
            boolean allowed = properties.getAllowedHosts().stream().anyMatch(host -> host.equalsIgnoreCase(uri.getHost()));
            if (!validScheme || !clean || !allowed) throw new DomainException("La URL de WPPConnect no está permitida por TIENDA_WPPCONNECT_ALLOWED_HOSTS.");
            return URI.create(uri.getScheme().toLowerCase() + "://" + uri.getAuthority());
        } catch (DomainException ex) { throw ex; }
        catch (Exception ex) { throw new DomainException("La URL de WPPConnect no es válida."); }
    }
}
