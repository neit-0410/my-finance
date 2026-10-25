package com.neit.personal_finance.idempotency;

import com.neit.personal_finance.entities.IdempotencyRecord;
import com.neit.personal_finance.repositories.jpa.IdempotencyRepository;
import com.neit.personal_finance.sercurity.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.HexFormat;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final CurrentUser currentUser;
    private final IdempotencyRepository records;
    private final ObjectMapper json;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<String> execute(String key, String operation, Object request, Supplier<?> action) {
        // Authentication is checked before looking up any stored response.
        var owner = currentUser.get();
        if (key == null) return response(json.writeValueAsString(action.get()), false);
        if (!key.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key (1-128 ASCII characters)");
        }
        // Shared MySQL lock works across backend replicas, including existing stock/cashbook writes.
        currentUser.lock();
        String keyHash = hash(key);
        String fingerprint = hash(operation + "\n" + json.writeValueAsString(request));
        var existing = records.findByUser_IdAndKeyHash(owner.getId(), keyHash);
        if (existing.isPresent()) {
            if (!existing.get().getFingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key already used for a different request");
            }
            return response(existing.get().getResponseBody(), true);
        }
        String body = json.writeValueAsString(action.get());
        var record = new IdempotencyRecord();
        record.setUser(owner);
        record.setKeyHash(keyHash);
        record.setFingerprint(fingerprint);
        record.setResponseBody(body);
        record.setCreatedAt(Instant.now());
        records.save(record);
        return response(body, false);
    }

    private ResponseEntity<String> response(String body, boolean replayed) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Replayed", Boolean.toString(replayed)).body(body);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
