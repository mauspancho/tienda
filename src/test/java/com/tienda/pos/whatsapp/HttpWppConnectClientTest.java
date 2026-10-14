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
import static org.assertj.core.api.Assertions.assertThat;

class HttpWppConnectClientTest {
    private HttpServer server;
    private WhatsappProvider provider;
    private HttpWppConnectClient client;

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
        WhatsappProperties properties = new WhatsappProperties();
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
    private byte[] png() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }
}
