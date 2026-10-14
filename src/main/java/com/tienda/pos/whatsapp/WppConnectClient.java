package com.tienda.pos.whatsapp;

import java.util.List;

public interface WppConnectClient {
    record RemoteSession(String name, String status) { }
    record SessionSnapshot(WhatsappSessionStatus status, String phoneNumber) { }
    record RemoteGroup(String id, String name, Integer participants) { }
    record SendResult(boolean success, String messageId, String detail) { }

    List<RemoteSession> listSessions(WhatsappProvider provider, String secret);
    String generateSessionToken(WhatsappProvider provider, String secret, String sessionName);
    SessionSnapshot start(WhatsappProvider provider, String sessionName, String token);
    SessionSnapshot status(WhatsappProvider provider, String sessionName, String token);
    byte[] qrCode(WhatsappProvider provider, String sessionName, String token);
    List<RemoteGroup> groups(WhatsappProvider provider, String sessionName, String token);
    SendResult sendText(WhatsappProvider provider, String sessionName, String token, String phone, boolean group, String message);
    SendResult sendFile(WhatsappProvider provider, String sessionName, String token, String phone, boolean group,
                        String filename, String contentType, byte[] bytes, String caption);
}
