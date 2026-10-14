package com.tienda.pos.whatsapp;

import com.tienda.pos.exception.DomainException;
import com.tienda.pos.common.NormalMode;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
@NormalMode
public class WhatsappCryptoService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final WhatsappProperties properties;

    public WhatsappCryptoService(WhatsappProperties properties) { this.properties = properties; }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) throw new DomainException("La credencial no puede estar vacía.");
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(packed);
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible cifrar la credencial.", ex);
        }
    }

    public String decrypt(String encoded) {
        try {
            if (encoded == null || !encoded.startsWith("v1:")) throw new IllegalArgumentException("Formato inválido");
            byte[] packed = Base64.getDecoder().decode(encoded.substring(3));
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(packed, 12, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible descifrar la credencial de WhatsApp.", ex);
        }
    }

    public String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private SecretKeySpec key() {
        String configured = properties.getMasterKey() == null ? "" : properties.getMasterKey().trim();
        if (configured.isBlank()) {
            throw new DomainException("Configura TIENDA_WPPCONNECT_MASTER_KEY antes de guardar credenciales.");
        }
        byte[] raw;
        try { raw = Base64.getDecoder().decode(configured); }
        catch (IllegalArgumentException ex) { throw new DomainException("TIENDA_WPPCONNECT_MASTER_KEY debe estar en Base64."); }
        if (raw.length != 32) throw new DomainException("TIENDA_WPPCONNECT_MASTER_KEY debe contener 32 bytes en Base64.");
        return new SecretKeySpec(raw, "AES");
    }
}
