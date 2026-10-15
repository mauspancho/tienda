package com.tienda.pos.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpWppConnectClientTest {
    private HttpServer server;
    private WhatsappProvider provider;
    private HttpWppConnectClient client;
    private WhatsappProperties properties;

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/test/list-chats", exchange -> json(exchange, """
                {"response":[
                  {"id":{"_serialized":"120363111@g.us"},"name":"Ofertas","participants":[{},{}]},
                  {"id":{"_serialized":"5215512345678@c.us"},"name":"Persona"},
                  {"chatId":"52155-99@g.us","formattedTitle":"Clientes"}
                ]}
                """));
        server.createContext("/api/test/check-connection-session", exchange -> json(exchange,
                "{\"response\":{\"status\":\"CONNECTED\",\"phoneNumber\":\"5215512345678@c.us\"}}"));
        server.createContext("/api/bool/check-connection-session", exchange -> json(exchange,
                "{\"status\":true,\"message\":\"Connected\"}"));
        server.createContext("/api/bool/get-phone-number", exchange -> json(exchange,
                "{\"response\":{\"phoneNumber\":\"5215599999999@c.us\"}}"));
        server.createContext("/api/host/check-connection-session", exchange -> json(exchange,
                "{\"status\":true,\"message\":\"Connected\"}"));
        server.createContext("/api/host/get-phone-number", exchange -> error(exchange, 404));
        server.createContext("/api/host/host-device", exchange -> json(exchange,
                "{\"response\":{\"id\":{\"user\":\"5215588888888\",\"server\":\"c.us\"}}}"));
        server.createContext("/api/secret/show-all-sessions", exchange -> json(exchange,
                "[{\"session\":\"tienda\",\"status\":\"CONNECTED\"}]"));
        server.createContext("/api/qr-json/qrcode-session", exchange -> json(exchange,
                "{\"response\":{\"base64Qr\":\"data:image/png;base64," + Base64.getEncoder().encodeToString(png()) + "\"}}"));
        server.createContext("/api/qr-binary/qrcode-session", exchange -> image(exchange, png()));
        server.start();
        properties = new WhatsappProperties();
        properties.setAllowedHosts(List.of("127.0.0.1"));
        provider = new WhatsappProvider(); provider.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new HttpWppConnectClient(new ObjectMapper(), new ProviderUrlValidator(properties), properties);
    }
    @AfterEach void tearDown() { if (server != null) server.stop(0); }

    @Test void normalizesGroupsAndPreservesGroupSuffix() {
        var groups = client.groups(provider, "test", "token");
        assertThat(groups).extracting(WppConnectClient.RemoteGroup::id).containsExactly("52155-99@g.us", "120363111@g.us");
        assertThat(groups).extracting(WppConnectClient.RemoteGroup::name).containsExactly("Clientes", "Ofertas");
        assertThat(groups.get(1).participants()).isEqualTo(2);
    }
    @Test void readsSessionStatusAndExistingSessions() {
        assertThat(client.status(provider, "test", "token").status()).isEqualTo(WhatsappSessionStatus.CONNECTED);
        assertThat(client.status(provider, "test", "token").phoneNumber()).isEqualTo("5215512345678");
        assertThat(client.status(provider, "bool", "token").status()).isEqualTo(WhatsappSessionStatus.CONNECTED);
        assertThat(client.status(provider, "bool", "token").phoneNumber()).isEqualTo("5215599999999");
        assertThat(client.status(provider, "host", "token").phoneNumber()).isEqualTo("5215588888888");
        assertThat(client.listSessions(provider, "secret")).extracting(WppConnectClient.RemoteSession::name).containsExactly("tienda");
    }
    @Test void readsQrFromNestedBase64AndBinaryImage() {
        assertThat(client.qrCode(provider, "qr-json", "token")).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThat(client.qrCode(provider, "qr-binary", "token")).isEqualTo(png());
    }
    @Test void paginatesFiveHundredGroupsWithSupportedCursorParameters() {
        List<String> payloads = new CopyOnWriteArrayList<>();
        server.createContext("/api/many/list-chats", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            payloads.add(body);
            if (body.contains("group-499@g.us")) {
                json(exchange, "{\"response\":[]}");
                return;
            }
            int start = body.contains("group-399@g.us") ? 400 : body.contains("group-299@g.us") ? 300
                    : body.contains("group-199@g.us") ? 200 : body.contains("group-099@g.us") ? 100 : 0;
            json(exchange, groupsJson(start, start + 100));
        });

        var groups = client.groups(provider, "many", "token");

        assertThat(groups).hasSize(500);
        assertThat(payloads).hasSize(6);
        assertThat(payloads.getFirst()).contains("\"count\":100", "\"ignoreGroupMetadata\":true")
                .doesNotContain("\"page\"");
        assertThat(payloads.get(1)).contains("\"id\":\"group-099@g.us\"", "\"direction\":\"after\"");
    }

    @Test void rejectsRepeatedPagesInsteadOfAcceptingPartialSynchronization() {
        server.createContext("/api/repeated/list-chats", exchange -> json(exchange, groupsJson(0, 100)));

        assertThatThrownBy(() -> client.groups(provider, "repeated", "token"))
                .isInstanceOf(WppConnectException.class)
                .hasMessageContaining("repitió una página");
    }

    @Test void usesDedicatedTimeoutForGroupSynchronization() {
        properties.setGroupSyncReadTimeout(java.time.Duration.ofMillis(50));
        server.createContext("/api/slow/list-chats", exchange -> {
            try { Thread.sleep(200); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            json(exchange, "{\"response\":[]}");
        });

        assertThatThrownBy(() -> client.groups(provider, "slow", "token"))
                .isInstanceOf(WppConnectException.class)
                .hasMessageContaining("tiempo de espera");
    }
    private void json(HttpExchange exchange, String value) throws java.io.IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
    private void image(HttpExchange exchange, byte[] bytes) throws java.io.IOException {
        exchange.getResponseHeaders().add("Content-Type", "image/png"); exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
    private void error(HttpExchange exchange, int status) throws java.io.IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }
    private String groupsJson(int start, int end) {
        StringBuilder value = new StringBuilder("{\"response\":[");
        for (int index = start; index < end; index++) {
            if (index > start) value.append(',');
            value.append("{\"id\":{\"_serialized\":\"group-")
                    .append(String.format("%03d", index)).append("@g.us\"},\"name\":\"Grupo ")
                    .append(index).append("\"}");
        }
        return value.append("]}").toString();
    }
    private byte[] png() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }
}
