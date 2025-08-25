package com.igot.cb.notificationUtill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.metrics.service.ApiMetricsTracker;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

class HelperMethodServiceTest {

    @InjectMocks
    private HelperMethodService service;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void test_fetchDataForKeys_userData_success() throws Exception {
        List<String> keys = List.of("key1", "key2");
        List<Object> values = List.of("{\"name\":\"test\"}", "{\"id\":\"123\"}");

        when(cacheService.hget(keys)).thenReturn(values);
        when(objectMapper.readValue("{\"name\":\"test\"}", Object.class)).thenReturn(Map.of("name", "test"));
        when(objectMapper.readValue("{\"id\":\"123\"}", Object.class)).thenReturn(Map.of("id", "123"));

        List<Object> result = service.fetchDataForKeys(keys, true);

        assertEquals(2, result.size());
        verify(cacheService).hget(keys);
    }

    @Test
    void test_fetchDataForKeys_nonUserData_success() throws Exception {
        List<String> keys = List.of("key1");
        List<Object> values = List.of("{\"data\":\"value\"}");

        when(cacheService.hgetMulti(keys)).thenReturn(values);
        when(objectMapper.readValue("{\"data\":\"value\"}", Object.class)).thenReturn(Map.of("data", "value"));

        List<Object> result = service.fetchDataForKeys(keys, false);

        assertEquals(1, result.size());
        verify(cacheService).hgetMulti(keys);
    }

    @Test
    void test_fetchDataForKeys_withNullValues() {
        List<String> keys = List.of("key1", "key2");
        List<Object> values = Arrays.asList("data", null);

        when(cacheService.hget(keys)).thenReturn(values);

        List<Object> result = service.fetchDataForKeys(keys, true);

        assertEquals(1, result.size());
    }

    @Test
    void test_fetchDataForKeys_jsonException() throws Exception {
        List<String> keys = List.of("key1");
        List<Object> values = List.of("invalid-json");

        when(cacheService.hget(keys)).thenReturn(values);
        when(objectMapper.readValue("invalid-json", Object.class)).thenThrow(new JsonProcessingException("error") {});

        List<Object> result = service.fetchDataForKeys(keys, true);

        assertEquals(1, result.size());
    }

    @Test
    void test_fetchUserFromPrimary_success() throws Exception {
        List<String> userIds = List.of("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", "user1");
        userInfo.put("firstname", "John");
        userInfo.put("profiledetails", "{\"profileImageUrl\":\"img.jpg\",\"designation\":\"dev\",\"employmentDetails\":{\"departmentName\":\"IT\"}}");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(List.of(userInfo));
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(Map.of("profileImageUrl", "img.jpg", "designation", "dev",
                        "employmentDetails", Map.of("departmentName", "IT")));

        try (MockedStatic<ApiMetricsTracker> tracker = mockStatic(ApiMetricsTracker.class)) {
            tracker.when(ApiMetricsTracker::isTrackingEnabled).thenReturn(true);

            List<Object> result = service.fetchUserFromPrimary(userIds);

            assertEquals(1, result.size());
            Map<String, Object> user = (Map<String, Object>) result.get(0);
            assertEquals("user1", user.get("user_id"));
            assertEquals("John", user.get("first_name"));
            tracker.verify(() -> ApiMetricsTracker.recordDbOperation(anyString(), anyString(), anyString(), anyLong()));
        }
    }

    @Test
    void test_fetchUserFromPrimary_emptyProfileDetails() {
        List<String> userIds = List.of("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", "user1");
        userInfo.put("firstname", "John");
        userInfo.put("profiledetails", "");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(List.of(userInfo));

        List<Object> result = service.fetchUserFromPrimary(userIds);

        assertEquals(1, result.size());
        Map<String, Object> user = (Map<String, Object>) result.get(0);
        assertEquals("user1", user.get("user_id"));
    }

    @Test
    void test_fetchUserFromPrimary_jsonException() throws Exception {
        List<String> userIds = List.of("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", "user1");
        userInfo.put("firstname", "John");
        userInfo.put("profiledetails", "invalid-json");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(List.of(userInfo));
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("error") {});

        List<Object> result = service.fetchUserFromPrimary(userIds);

        assertEquals(1, result.size());
    }

    @Test
    void test_fetchUserFirstName_fromRedis() throws Exception {
        when(cacheService.hget(anyList())).thenReturn(List.of("{\"first_name\":\"RedisUser\"}"));
        when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(Map.of("first_name", "RedisUser"));

        String result = service.fetchUserFirstName("user1");

        assertEquals("RedisUser", result);
    }

    @Test
    void test_fetchUserFirstName_redisBlankName() throws Exception {
        when(cacheService.hget(anyList())).thenReturn(List.of("{\"first_name\":\"\"}"));
        when(objectMapper.readValue(anyString(), eq(Object.class))).thenReturn(Map.of("first_name", ""));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(List.of());

        String result = service.fetchUserFirstName("user1");

        assertEquals("User", result);
    }
}
