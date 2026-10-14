package com.tienda.pos.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.common.NormalMode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@NormalMode
public class HttpWppConnectClient implements WppConnectClient {
    private final ObjectMapper mapper;
    private final ProviderUrlValidator urls;
    private final WhatsappProperties properties;

    public HttpWppConnectClient(ObjectMapper mapper, ProviderUrlValidator urls, WhatsappProperties properties) {
        this.mapper = mapper; this.urls = urls; this.properties = properties;
    }

    @Override
    public List<RemoteSession> listSessions(WhatsappProvider provider, String secret) {
        JsonNode root = request(provider, null, "GET", "/api/" + segment(secret) + "/show-all-sessions", null);
        List<RemoteSession> result = new ArrayList<>();
        JsonNode array = firstArray(root);
        if (array != null) for (JsonNode item : array) {
            String name = firstText(item, "session", "sessionName", "name");
            if (name != null) result.add(new RemoteSession(name, firstText(item, "status", "state")));
        }
        return result;
    }

    @Override
    public String generateSessionToken(WhatsappProvider provider, String secret, String sessionName) {
        JsonNode root = request(provider, null, "POST", "/api/" + segment(sessionName) + "/" + segment(secret) + "/generate-token", Map.of());
        String token = firstTextRecursive(root, Set.of("token"));
        if (token == null) token = firstTextRecursive(root, Set.of("full"));
        if (token == null || token.isBlank()) throw new WppConnectException(WppConnectException.Kind.INCOMPATIBLE, "WPPConnect no devolvió un token de sesión.");
        return token.startsWith("wppconnect:") ? token.substring("wppconnect:".length()) : token;
    }

    @Override
    public SessionSnapshot start(WhatsappProvider provider, String sessionName, String token) {
        request(provider, token, "POST", "/api/" + segment(sessionName) + "/start-session", Map.of());
        return status(provider, sessionName, token);
    }

    @Override
    public SessionSnapshot status(WhatsappProvider provider, String sessionName, String token) {
        JsonNode root = request(provider, token, "GET", "/api/" + segment(sessionName) + "/check-connection-session", null);
        String raw = firstTextRecursive(root, Set.of("status", "state", "sessionStatus", "message"));
        String phone = firstTextRecursive(root, Set.of("phone", "phoneNumber", "wid", "user"));
        WhatsappSessionStatus status = mapStatus(raw, root);
        if (status == WhatsappSessionStatus.CONNECTED && normalizePhone(phone) == null) {
            phone = connectedPhone(provider, sessionName, token);
        }
        return new SessionSnapshot(status, normalizePhone(phone));
    }

    private String connectedPhone(WhatsappProvider provider, String sessionName, String token) {
        for (String endpoint : List.of("/get-phone-number", "/host-device")) {
            try {
                JsonNode root = request(provider, token, "GET", "/api/" + segment(sessionName) + endpoint, null);
                String phone = phoneIdentity(root);
                if (phone != null) return phone;
            } catch (WppConnectException ignored) {
                // Versions of WPPConnect differ in which identity endpoint they expose.
            }
        }
        return null;
    }

    private String phoneIdentity(JsonNode root) {
        if (root == null) return null;
        if (root.isValueNode() && !root.asText().isBlank()) return root.asText();
        return firstTextRecursive(root, Set.of("phone", "phoneNumber", "wid", "user", "_serialized", "serialized"));
    }

    @Override
    public byte[] qrCode(WhatsappProvider provider, String sessionName, String token) {
        RawResponse response = rawRequest(provider, token, "/api/" + segment(sessionName) + "/qrcode-session");
        byte[] direct = normalizeQrImage(response.body());
        if (direct != null) return direct;

        String raw = new String(response.body(), StandardCharsets.UTF_8).trim();
        try {
            JsonNode root = mapper.readTree(raw);
            byte[] decoded = decodeQrValue(qrImageValue(root));
            if (decoded != null) return decoded;
        } catch (Exception ignored) { }
        byte[] decoded = decodeQrValue(raw);
        if (decoded != null) return decoded;
        throw new WppConnectException(WppConnectException.Kind.INCOMPATIBLE,
                "WPPConnect todavía no devolvió una imagen QR válida.");
    }

    @Override
    public List<RemoteGroup> groups(WhatsappProvider provider, String sessionName, String token) {
        Map<String, RemoteGroup> groups = new LinkedHashMap<>();
        for (int page = 1; page <= 50; page++) {
            JsonNode root;
            try {
                root = request(provider, token, "POST", "/api/" + segment(sessionName) + "/list-chats",
                        Map.of("onlyGroups", true, "count", 100, "page", page));
            } catch (WppConnectException ex) {
                if (page == 1 && ex.getKind() == WppConnectException.Kind.INCOMPATIBLE) {
                    root = request(provider, token, "GET", "/api/" + segment(sessionName) + "/all-chats", null);
                } else throw ex;
            }
            List<JsonNode> items = chatItems(root);
            int before = groups.size();
            for (JsonNode item : items) {
                String id = chatId(item);
                if (id != null && id.endsWith("@g.us")) {
                    String name = firstText(item, "name", "formattedTitle", "pushname", "subject");
                    Integer count = participantCount(item);
                    groups.put(id, new RemoteGroup(id, name == null || name.isBlank() ? id : name, count));
                }
            }
            if (items.size() < 100 || groups.size() == before) break;
        }
        return groups.values().stream().sorted(Comparator.comparing(RemoteGroup::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Override
    public SendResult sendText(WhatsappProvider provider, String sessionName, String token, String phone, boolean group, String message) {
        JsonNode root = request(provider, token, "POST", "/api/" + segment(sessionName) + "/send-message",
                Map.of("phone", phone, "isGroup", group, "message", message));
        return sendResult(root);
    }

    @Override
    public SendResult sendFile(WhatsappProvider provider, String sessionName, String token, String phone, boolean group,
                               String filename, String contentType, byte[] bytes, String caption) {
        String base64 = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone); body.put("isGroup", group); body.put("filename", filename);
        body.put("caption", caption == null ? "" : caption); body.put("base64", base64);
        String endpoint = contentType.startsWith("image/") ? "/send-image" : "/send-file-base64";
        JsonNode root = request(provider, token, "POST", "/api/" + segment(sessionName) + endpoint, body);
        return sendResult(root);
    }

    private JsonNode request(WhatsappProvider provider, String token, String method, String path, Object body) {
        var base = urls.validate(provider.getBaseUrl());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        RestClient client = RestClient.builder().baseUrl(base.toString()).requestFactory(factory).build();
        try {
            RestClient.RequestHeadersSpec<?> spec;
            if ("POST".equals(method)) spec = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body == null ? Map.of() : body);
            else spec = client.get().uri(path);
            if (token != null) spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return spec.exchange((request, response) -> {
                int code = response.getStatusCode().value();
                String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                if (code == 401 || code == 403) throw new WppConnectException(WppConnectException.Kind.INVALID_CREDENTIALS, "WPPConnect rechazó las credenciales.");
                if (code == 404) throw new WppConnectException(WppConnectException.Kind.INCOMPATIBLE, "La versión de WPPConnect no ofrece la operación solicitada.");
                if (code < 200 || code >= 300) throw new WppConnectException(WppConnectException.Kind.REMOTE_ERROR, "WPPConnect respondió HTTP " + code + ".");
                try { return raw.isBlank() ? mapper.createObjectNode() : mapper.readTree(raw); }
                catch (Exception ex) { throw new WppConnectException(WppConnectException.Kind.INCOMPATIBLE, "WPPConnect devolvió una respuesta incompatible.", ex); }
            });
        } catch (WppConnectException ex) { throw ex; }
        catch (ResourceAccessException ex) {
            boolean timeout = hasCause(ex, SocketTimeoutException.class);
            throw new WppConnectException(timeout ? WppConnectException.Kind.TIMEOUT : WppConnectException.Kind.UNREACHABLE,
                    timeout ? "WPPConnect agotó el tiempo de espera." : "No fue posible conectar con WPPConnect.", ex);
        }
    }

    private RawResponse rawRequest(WhatsappProvider provider, String token, String path) {
        var base = urls.validate(provider.getBaseUrl());
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        RestClient client = RestClient.builder().baseUrl(base.toString()).requestFactory(factory).build();
        try {
            RestClient.RequestHeadersSpec<?> spec = client.get().uri(path);
            if (token != null) spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            return spec.exchange((request, response) -> {
                int code = response.getStatusCode().value();
                byte[] bytes = response.getBody().readAllBytes();
                if (code == 401 || code == 403) throw new WppConnectException(WppConnectException.Kind.INVALID_CREDENTIALS, "WPPConnect rechazó las credenciales.");
                if (code == 404) throw new WppConnectException(WppConnectException.Kind.INCOMPATIBLE, "La versión de WPPConnect no ofrece la operación solicitada.");
                if (code < 200 || code >= 300) throw new WppConnectException(WppConnectException.Kind.REMOTE_ERROR, "WPPConnect respondió HTTP " + code + ".");
                return new RawResponse(bytes);
            });
        } catch (WppConnectException ex) { throw ex; }
        catch (ResourceAccessException ex) {
            boolean timeout = hasCause(ex, SocketTimeoutException.class);
            throw new WppConnectException(timeout ? WppConnectException.Kind.TIMEOUT : WppConnectException.Kind.UNREACHABLE,
                    timeout ? "WPPConnect agotó el tiempo de espera." : "No fue posible conectar con WPPConnect.", ex);
        }
    }

    private String qrImageValue(JsonNode node) {
        if (node == null) return null;
        if (node.isTextual()) return node.asText();
        if (node.isObject()) {
            Set<String> imageKeys = Set.of("qrcode", "qrcodebase64", "base64qr", "base64", "qr", "image", "urlcode");
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (imageKeys.contains(field.getKey().toLowerCase(Locale.ROOT)) && field.getValue().isTextual()) {
                    String value = field.getValue().asText();
                    if (!value.isBlank() && (!field.getKey().equalsIgnoreCase("urlCode") || value.startsWith("data:image/"))) return value;
                }
            }
        }
        if (node.isContainerNode()) for (JsonNode child : node) {
            if (!child.isContainerNode()) continue;
            String value = qrImageValue(child);
            if (value != null) return value;
        }
        return null;
    }

    private byte[] decodeQrValue(String value) {
        if (value == null || value.isBlank()) return null;
        String encoded = value.trim();
        int comma = encoded.indexOf(',');
        if (encoded.startsWith("data:image/") && comma >= 0) encoded = encoded.substring(comma + 1);
        try { return normalizeQrImage(Base64.getMimeDecoder().decode(encoded)); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private byte[] normalizeQrImage(byte[] value) {
        if (value == null || value.length < 8) return null;
        if (value[0] == (byte) 0x89 && value[1] == 0x50 && value[2] == 0x4e && value[3] == 0x47) return value;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(value));
            if (image == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) return null;
            return output.toByteArray();
        } catch (Exception ex) { return null; }
    }

    private record RawResponse(byte[] body) { }

    private boolean hasCause(Throwable value, Class<? extends Throwable> type) {
        for (Throwable item = value; item != null; item = item.getCause()) if (type.isInstance(item)) return true;
        return false;
    }
    private String segment(String value) { return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8); }
    private SendResult sendResult(JsonNode root) {
        String id = firstTextRecursive(root, Set.of("id", "messageId", "serialized"));
        String detail = firstTextRecursive(root, Set.of("message", "status"));
        boolean success = !root.has("status") || root.path("status").asBoolean(true) || "success".equalsIgnoreCase(root.path("status").asText());
        return new SendResult(success, id, detail);
    }
    private WhatsappSessionStatus mapStatus(String raw, JsonNode root) {
        String value = (raw == null ? "" : raw) + " " + root;
        value = value.toLowerCase(Locale.ROOT);
        if (value.contains("disconnected") || value.contains("not connected") || value.contains("closed")) return WhatsappSessionStatus.DISCONNECTED;
        if (value.contains("islogged") || value.contains("connected") || value.contains("open")) return WhatsappSessionStatus.CONNECTED;
        if (value.contains("qr")) return WhatsappSessionStatus.WAITING_QR;
        if (value.contains("start")) return WhatsappSessionStatus.STARTING;
        if (value.contains("connect")) return WhatsappSessionStatus.CONNECTING;
        return WhatsappSessionStatus.DISCONNECTED;
    }
    private String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replace("@c.us", "").replace("@s.whatsapp.net", "");
        return normalized.isBlank() ? null : normalized;
    }
    private List<JsonNode> chatItems(JsonNode root) {
        JsonNode array = firstArray(root);
        if (array == null) return List.of();
        List<JsonNode> result = new ArrayList<>(); array.forEach(result::add); return result;
    }
    private JsonNode firstArray(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) return node;
        if (node.isContainerNode()) for (JsonNode child : node) { JsonNode found = firstArray(child); if (found != null) return found; }
        return null;
    }
    private String chatId(JsonNode item) {
        String direct = firstText(item, "chatId", "serializedId", "wid");
        if (direct != null) return direct;
        JsonNode id = item.path("id");
        if (id.isTextual()) return id.asText();
        String serialized = firstText(id, "_serialized", "serialized", "id");
        if (serialized != null) return serialized;
        String user = firstText(id, "user"); String server = firstText(id, "server");
        return user != null && server != null ? user + "@" + server : null;
    }
    private Integer participantCount(JsonNode item) {
        JsonNode participants = item.path("participants");
        if (participants.isArray()) return participants.size();
        JsonNode count = item.path("participantCount");
        return count.isNumber() ? count.asInt() : null;
    }
    private String firstText(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) { JsonNode value = node.get(name); if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText(); }
        return null;
    }
    private String firstTextRecursive(JsonNode node, Set<String> names) {
        if (node == null) return null;
        if (node.isObject()) {
            for (String name : names) { JsonNode value = node.get(name); if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText(); }
        }
        if (node.isContainerNode()) for (JsonNode child : node) { String found = firstTextRecursive(child, names); if (found != null) return found; }
        return null;
    }
}
