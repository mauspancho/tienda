package com.tienda.pos.api.v1.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.pos.api.v1.error.ApiConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

@com.tienda.pos.common.NormalMode
@Service
public class ApiIdempotencyService {
    private final com.tienda.pos.tenant.CurrentTenant currentTenant;

    private final ApiIdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final com.tienda.pos.user.AppUserRepository users;

    public ApiIdempotencyService(ApiIdempotencyRepository repository, ObjectMapper objectMapper, com.tienda.pos.user.AppUserRepository users, com.tienda.pos.tenant.CurrentTenant currentTenant) {
        this.currentTenant = currentTenant;
        this.users = users;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> T execute(String username, String operation, String idempotencyKey, Object request,
                         Class<T> responseType, Supplier<T> action) {
        Long tenantId = currentTenant.id();
        if (!username.equals(com.tienda.pos.common.CurrentUser.username())) {
            throw new org.springframework.security.access.AccessDeniedException("Usuario no autorizado.");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key excede 200 caracteres.");
        }
        String keyHash = hash(normalizedKey);
        String requestHash = hash(json(request));
        // The database lock survives until commit, including across application instances.
        var user = users.lockForApiWrite(username, tenantId).orElseThrow();
        return repository.findByTenantIdAndUserIdAndOperationNameAndKeyHash(tenantId, user.getId(), operation, keyHash)
                .map(existing -> replay(existing, requestHash, responseType))
                .orElseGet(() -> executeAndStore(tenantId, user.getId(), username, operation, keyHash, requestHash, action));
    }

    private <T> T executeAndStore(Long tenantId, Long userId, String username, String operation, String keyHash, String requestHash,
                                  Supplier<T> action) {
        T response = action.get();
        ApiIdempotencyRecord record = new ApiIdempotencyRecord();
        record.setTenantId(tenantId);
        record.setUserId(userId);
        record.setUsername(username);
        record.setOperationName(operation);
        record.setKeyHash(keyHash);
        record.setRequestHash(requestHash);
        record.setResponseJson(json(response));
        repository.save(record);
        return response;
    }

    private <T> T replay(ApiIdempotencyRecord existing, String requestHash, Class<T> responseType) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiConflictException("Idempotency-Key ya fue utilizada con una solicitud diferente.");
        }
        try {
            return objectMapper.readValue(existing.getResponseJson(), responseType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible recuperar la respuesta idempotente.", ex);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("No fue posible procesar la solicitud.", ex);
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
