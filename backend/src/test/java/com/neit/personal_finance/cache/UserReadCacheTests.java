package com.neit.personal_finance.cache;

import com.neit.personal_finance.entities.User;
import com.neit.personal_finance.repositories.jpa.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserReadCacheTests {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String,String> values = mock(ValueOperations.class);
    private final UserRepository users = mock(UserRepository.class);
    private final User owner = User.builder().id(77).cacheRevision(3).build();
    private UserReadCache cache(boolean enabled) {
        when(redis.opsForValue()).thenReturn(values);
        return new UserReadCache(redis, JsonMapper.builder().build(), users, enabled, 60);
    }
    @Test void entityReadModelsCanRoundTripWithoutCredentials() {
        var mapper = JsonMapper.builder().build();
        var user = User.builder().id(1).username("fixture").password("not-a-real-password").build();
        var category = com.neit.personal_finance.entities.Category.builder().id(1L).name("Food")
                .type(com.neit.personal_finance.entities.Category.Type.EXPENSE).user(user).build();
        String encoded = mapper.writeValueAsString(List.of(category));
        assertFalse(encoded.contains("password"));
        var decoded = mapper.readValue(encoded, new TypeReference<List<com.neit.personal_finance.entities.Category>>() {});
        assertEquals("Food", decoded.get(0).getName());
    }
    @Test void hitDoesNotInvokeDatabaseLoader() {
        var cache=cache(true);when(values.get(anyString())).thenReturn("[\"cached\"]");
        var calls=new AtomicInteger();
        var result=cache.get(owner,"categories",new TypeReference<List<String>>(){},()->{calls.incrementAndGet();return List.of("db");});
        assertEquals(List.of("cached"),result);assertEquals(0,calls.get());
    }
    @Test void redisReadFailureFallsBackAndDoesNotHideDatabaseErrors() {
        var cache=cache(true);when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        assertEquals(List.of("db"),cache.get(owner,"categories",new TypeReference<List<String>>(){},()->List.of("db")));
        assertThrows(IllegalStateException.class,()->cache.get(owner,"categories",new TypeReference<List<String>>(){},()->{throw new IllegalStateException("DB failure");}));
    }
    @Test void redisWriteFailureDoesNotFailSuccessfulRead() {
        var cache=cache(true);
        doThrow(new RedisConnectionFailureException("offline")).when(values).set(anyString(),anyString(),any(Duration.class));
        assertEquals(List.of("db"),cache.get(owner,"categories",new TypeReference<List<String>>(){},()->List.of("db")));
    }
    @Test void malformedJsonFallsBackToDatabase() {
        var cache=cache(true);when(values.get(anyString())).thenReturn("not json");
        assertEquals(List.of("db"),cache.get(owner,"categories",new TypeReference<List<String>>(){},()->List.of("db")));
    }
    @Test void disabledCacheDoesNotContactRedis() {
        var cache=cache(false);clearInvocations(redis);
        assertEquals(List.of("db"),cache.get(owner,"categories",new TypeReference<List<String>>(){},()->List.of("db")));
        verifyNoInteractions(redis);
    }
}
