package com.neit.personal_finance.cache;

import com.neit.personal_finance.entities.User;
import com.neit.personal_finance.repositories.jpa.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Cache only authorized read results. SQL revisions make invalidation atomic with domain writes. */
@Component
public class UserReadCache {
    private static final Logger log = LoggerFactory.getLogger(UserReadCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final UserRepository users;
    private final boolean enabled;
    private final Duration ttl;
    private final AtomicBoolean warned = new AtomicBoolean();

    public UserReadCache(StringRedisTemplate redis, ObjectMapper json, UserRepository users,
                         @Value("${app.cache.enabled:true}") boolean enabled,
                         @Value("${app.cache.ttl-seconds:60}") long ttlSeconds) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("Cache TTL must be positive");
        this.redis = redis;
        this.json = json;
        this.users = users;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public static String key(User owner, String resource) {
        return "finance:cache:v1:user:" + owner.getId() + ":revision:" + owner.getCacheRevision() + ":" + resource;
    }

    public <T> T get(User owner, String resource, TypeReference<T> type, Supplier<T> loader) {
        if (!enabled) return loader.get();
        String key = key(owner, resource);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                T value = json.readValue(cached, type);
                if (value != null) { warned.set(false); return value; }
            }
        } catch (RuntimeException ex) {
            warn(ex);
            // Do not call the loader inside this catch boundary: domain/DB failures must propagate.
            return loader.get();
        }
        T value = loader.get();
        if (value != null) {
            try {
                // Explicit types, no Java serialization/default typing. @JsonIgnore excludes credentials.
                redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
                warned.set(false);
            } catch (RuntimeException ex) { warn(ex); }
        }
        return value;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidate(long userId) {
        // This increment commits/rolls back with the same MySQL transaction as the mutation.
        // Redis availability and concurrent cache fills cannot restore an older revision.
        if (users.advanceCacheRevision(userId) != 1) throw new IllegalStateException("Cache owner is missing");
    }

    private void warn(RuntimeException ex) {
        if (warned.compareAndSet(false, true)) log.warn("Read cache failed ({}); serving database results instead", ex.getClass().getSimpleName());
    }
}
