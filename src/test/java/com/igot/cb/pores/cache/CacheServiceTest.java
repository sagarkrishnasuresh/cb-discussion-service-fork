package com.igot.cb.pores.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheServiceTest {

    @InjectMocks
    private CacheService cacheService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisTemplate<String, String> redisDataTemplate;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisDataTemplate.opsForValue()).thenReturn(valueOperations);

        // Set private field 'cacheTtl' using reflection
        Field cacheTtlField = CacheService.class.getDeclaredField("cacheTtl");
        cacheTtlField.setAccessible(true);
        cacheTtlField.set(cacheService, 60L); // set TTL to 60 seconds
    }

    @Test
    void testGetCache_Success() {
        String key = "testKey";
        String expectedValue = "cachedData";
        when(valueOperations.get(Constants.REDIS_KEY_PREFIX + key)).thenReturn(expectedValue);

        String result = cacheService.getCache(key);
        assertEquals(expectedValue, result);
    }

    @Test
    void testGetCache_Exception() {
        String key = "testKey";
        when(valueOperations.get(Constants.REDIS_KEY_PREFIX + key)).thenThrow(new RuntimeException("Redis error"));

        String result = cacheService.getCache(key);
        assertNull(result);
    }

    @Test
    void testPutCache_Success() throws JsonProcessingException {
        String key = "testKey";
        Object value = "data";
        when(objectMapper.writeValueAsString(value)).thenReturn("json");

        cacheService.putCache(key, value);
        verify(valueOperations, times(1)).set(Constants.REDIS_KEY_PREFIX + key, "json", 60L, TimeUnit.SECONDS);
    }

    @Test
    void testPutCache_Exception() throws JsonProcessingException {
        String key = "testKey";
        Object value = new Object();
        when(objectMapper.writeValueAsString(value)).thenThrow(JsonProcessingException.class);

        cacheService.putCache(key, value);
        // Exception handled inside the method, no assertions needed
    }

    @Test
    void testDeleteCache_Success() {
        String key = "testKey";
        when(redisTemplate.delete(Constants.REDIS_KEY_PREFIX + key)).thenReturn(true);

        Long result = cacheService.deleteCache(key);
        assertNull(result);
        verify(redisTemplate, times(1)).delete(Constants.REDIS_KEY_PREFIX + key);
    }

    @Test
    void testDeleteCache_NotFound() {
        String key = "testKey";
        when(redisTemplate.delete(Constants.REDIS_KEY_PREFIX + key)).thenReturn(false);

        Long result = cacheService.deleteCache(key);
        assertNull(result);
        verify(redisTemplate, times(1)).delete(Constants.REDIS_KEY_PREFIX + key);
    }

    @Test
    void testHget_Success() {
        List<String> keys = Arrays.asList("key1", "key2");
        when(valueOperations.get("key1")).thenReturn("val1");
        when(valueOperations.get("key2")).thenReturn("val2");

        List<Object> result = cacheService.hget(keys);

        assertEquals(2, result.size());
        assertEquals("val1", result.get(0));
        assertEquals("val2", result.get(1));
    }

    @Test
    void testHget_Exception() {
        List<String> keys = Arrays.asList("key1", "key2");
        when(valueOperations.get("key1")).thenThrow(new RuntimeException("Redis failure"));

        List<Object> result = cacheService.hget(keys);
        assertEquals(0, result.size()); // exception handled, list remains empty
    }

    @Test
    void testHgetMulti_ReturnsValuesFromRedis() {
        List<String> keys = Arrays.asList("key1", "key2");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(keys)).thenReturn(Arrays.asList("val1", "val2"));

        List<Object> result = cacheService.hgetMulti(keys);

        assertEquals(2, result.size());
        assertTrue(result.contains("val1"));
        assertTrue(result.contains("val2"));
    }
}
