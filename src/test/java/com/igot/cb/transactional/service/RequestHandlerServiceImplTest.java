package com.igot.cb.transactional.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.slf4j.Logger;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestHandlerServiceImplTest {

    @InjectMocks
    private RequestHandlerServiceImpl service;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Logger log;

    private ObjectMapper mockMapper;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        // Inject a mock logger
        Field logField = RequestHandlerServiceImpl.class.getDeclaredField("log");
        logField.setAccessible(true);
        logField.set(service, log);
        when(log.isDebugEnabled()).thenReturn(true);
        mockMapper = mock(ObjectMapper.class);
        Field mapperField = RequestHandlerServiceImpl.class.getDeclaredField("mapper");
        mapperField.setAccessible(true);
        mapperField.set(service, mockMapper);
    }

    @Test
    void testFetchResultUsingPost_successWithoutHeaders() throws Exception {
        Map<String, Object> expectedResponse = Map.of("key", "value");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expectedResponse);
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of("req", "data"), null);
        assertEquals(expectedResponse, result);
    }

    @Test
    void testFetchResultUsingPost_httpClientErrorException_parseSuccess() throws Exception {
        String errorJson = "{\"error\":\"bad request\"}";
        Map<String, Object> parsed = Map.of("error", "bad request");
        HttpClientErrorException hce = mock(HttpClientErrorException.class);
        when(hce.getResponseBodyAsString()).thenReturn(errorJson);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenThrow(hce);
        when(mockMapper.readValue(eq(errorJson), ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
                .thenReturn(parsed);
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        verify(log).error(contains("Error received"), eq(errorJson), eq(hce));
        assertEquals(parsed, result);
    }

    @Test
    void testFetchResultUsingPost_httpClientErrorException_parseFailure() throws Exception {
        String invalidJson = "not a json";
        HttpClientErrorException hce = mock(HttpClientErrorException.class);
        when(hce.getResponseBodyAsString()).thenReturn(invalidJson);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class))).thenThrow(hce);
        when(mockMapper.readValue(eq(invalidJson), ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
                .thenThrow(new RuntimeException("parse error"));
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        verify(log).error(contains("Error received"), eq(invalidJson), eq(hce));
        verify(log, atLeastOnce()).error(contains("Failed to parse error response"), any(), any());
        assertEquals(new HashMap<>(), result);
    }

    @Test
    void testFetchResultUsingPost_genericException() throws Exception {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Unexpected"));
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        verify(log).error(startsWith("Unexpected error"), any(), any());
        assertEquals(new HashMap<>(), result);
    }

    @Test
    void fetchResultUsingPost_handlesNullHeaders() throws Exception {
        Map<String, Object> expectedResponse = Map.of("result", 1);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expectedResponse);
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of("req", "data"), null);
        assertEquals(expectedResponse, result);
    }

    @Test
    void fetchResultUsingPost_handlesEmptyHeaders() throws Exception {
        Map<String, Object> expectedResponse = Map.of("result", 2);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expectedResponse);
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of("req", "data"), new HashMap<>());
        assertEquals(expectedResponse, result);
    }

    @Test
    void fetchResultUsingPost_handlesNullRequest() throws Exception {
        Map<String, Object> expectedResponse = Map.of("result", 3);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expectedResponse);
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", null, null);
        assertEquals(expectedResponse, result);
    }

    @Test
    void fetchResultUsingPost_handlesNullUri() throws Exception {
        when(restTemplate.postForObject(isNull(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost(null, Map.of("req", "data"), null);
        assertEquals(new HashMap<>(), result);
    }

    @Test
    void fetchResultUsingPost_returnsEmptyMapWhenResponseIsNull() throws Exception {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of("req", "data"), null);
        assertEquals(new HashMap<>(), result);
    }

    @Test
    void fetchResultUsingPost_handlesExceptionInErrorResponseParsing() throws Exception {
        String errorJson = "{invalid json}";
        HttpClientErrorException hce = mock(HttpClientErrorException.class);
        when(hce.getResponseBodyAsString()).thenReturn(errorJson);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(hce);
        when(mockMapper.readValue(eq(errorJson), ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
                .thenThrow(new RuntimeException("parse error"));
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        assertEquals(new HashMap<>(), result);
    }

    @Test
    void fetchResultUsingPost_triggersJsonProcessingExceptionAndCoversCatchBlock() throws Exception {
        when(mockMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("test") {
        });
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of("foo", "bar"), null);
        assertEquals(new HashMap<>(), result);
        verify(log).error(contains("JSON processing error"), any(), any());
    }

    @Test
    void fetchResultUsingPost_handlesExceptionInLoggingErrorResponse() throws Exception {
        when(mockMapper.writeValueAsString(any()))
                .thenReturn("{}") // for request logging
                .thenThrow(new JsonProcessingException("test") {
                }); // for error logging
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of("foo", "bar"), null);
        assertEquals(new HashMap<>(), result);
        verify(log).error(contains("JSON processing error"), any(), any());
        verify(log).error(contains("Failed to log error response"), any(), any());
    }


    @Test
    void testSuccessfulCall() throws Exception {
        Map<String, Object> expected = Map.of("key", "value");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expected);
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of("req", "data"), null);
        assertEquals(expected, result);
        verify(log, atLeastOnce()).debug(anyString(), any(), any(), any());
    }

    @Test
    void testHttpClientErrorException_parsesResponse() throws Exception {
        String errorJson = "{\"error\":\"bad request\"}";
        HttpClientErrorException hce = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null, errorJson.getBytes(), null
        );
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(hce);
        when(mockMapper.readValue(eq(errorJson), any(TypeReference.class)))
                .thenReturn(Map.of("error", "bad request"));
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        assertEquals(Map.of("error", "bad request"), result);
        verify(log).error(contains("Error received"), any(), eq(hce));
    }

    @Test
    void testHttpClientErrorException_parsingFails() throws Exception {
        String errorJson = "invalid json";
        HttpClientErrorException hce = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null, errorJson.getBytes(), null
        );
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(hce);
        when(mockMapper.readValue(eq(errorJson), any(TypeReference.class)))
                .thenThrow(new RuntimeException("parse error"));
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        assertEquals(new HashMap<>(), result);
        verify(log).error(contains("Failed to parse error response"), any(), any());
    }


    @Test
    void testGenericException() throws Exception {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Unexpected"));
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        assertEquals(new HashMap<>(), result);
        verify(log).error(contains("Unexpected error"), any(), any());
    }

    @Test
    void fetchResultUsingPost_withHeaders() throws Exception {
        Map<String, String> headers = Map.of("Authorization", "token");
        Map<String, Object> expected = Map.of("key", "value");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expected);
        when(mockMapper.writeValueAsString(any())).thenReturn("{}");
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), headers);
        assertEquals(expected, result);
    }

    @Test
    void fetchResultUsingPost_debugDisabled() throws Exception {
        when(log.isDebugEnabled()).thenReturn(false); // skip debug logs
        Map<String, Object> expected = Map.of("key", "value");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(expected);
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        assertEquals(expected, result);
    }

    @Test
    void fetchResultUsingPost_returnsEmptyMapWhenRestTemplateReturnsNull() throws Exception {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);
        Map<String, Object> result = service.fetchResultUsingPost("http://example.com", Map.of(), null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

}