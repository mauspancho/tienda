package com.tienda.pos.whatsapp;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhatsappCryptoServiceTest {
    @Test void encryptsWithRandomIvAndAuthenticatesCiphertext() {
        WhatsappProperties properties = new WhatsappProperties();
        properties.setMasterKey(Base64.getEncoder().encodeToString(new byte[32]));
        WhatsappCryptoService crypto = new WhatsappCryptoService(properties);
        String first = crypto.encrypt("secret-value");
        String second = crypto.encrypt("secret-value");
        assertThat(first).isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo("secret-value");
        char replacement = first.charAt(first.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = first.substring(0, first.length() - 1) + replacement;
        assertThatThrownBy(() -> crypto.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
