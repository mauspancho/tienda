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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class ApiIdempotencyService {

    private final ApiIdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ApiIdempotencyService(ApiIdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> T execute(String username, String operation, String idempotencyKey, Object request,
                         Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key excede 200 caracteres.");
        }
        String keyHash = hash(normalizedKey);
        String requestHash = hash(json(request));
        String lockName = username + ':' + operation + ':' + keyHash;
        ReentrantLock lock = locks.computeIfAbsent(lockName, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return repository.findByUsernameAndOperationNameAndKeyHash(username, operation, keyHash)
                    .map(existing -> replay(existing, requestHash, responseType))
                    .orElseGet(() -> executeAndStore(username, operation, keyHash, requestHash, action));
        } finally {
            lock.unlock();
            locks.remove(lockName, lock);
        }
    }

    private <T> T executeAndStore(String username, String operation, String keyHash, String requestHash,
                                  Supplier<T> action) {
        T response = action.get();
        ApiIdempotencyRecord record = new ApiIdempotencyRecord();
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
