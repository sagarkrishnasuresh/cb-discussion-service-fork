package com.igot.cb.notificationUtill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.igot.cb.pores.util.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

class NotificationTriggerServiceTest {


    @InjectMocks
    private NotificationTriggerService service;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "notificationApiUrl", "http://mock-url");
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
    }

    @Test
    void test_sendNotification_success() {
        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put("result", "ok");

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        ApiResponse result = service.sendNotification(
                "subCat", "subType", List.of("user1"), Map.of("msg", "hello")
        );

        assertEquals(HttpStatus.OK, result.getResponseCode());
        assertEquals(mockResponse, result.getResult());
        assertEquals("success", result.getParams().getStatus());
    }

    @Test
    void test_sendNotification_missingSubCategory() {
        ApiResponse result = service.sendNotification(
                "", "subType", List.of("user1"), Map.of("msg", "hello")
        );
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
        assertEquals("Failed", result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("subCategory is required"));
    }

    @Test
    void test_sendNotification_missingSubType() {
        ApiResponse result = service.sendNotification(
                "subCat", "", List.of("user1"), Map.of("msg", "hello")
        );
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
        assertEquals("Failed", result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("subType is required"));
    }

    @Test
    void test_sendNotification_emptyUserIds() {
        ApiResponse result = service.sendNotification(
                "subCat", "subType", Collections.emptyList(), Map.of("msg", "hello")
        );
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
        assertEquals("Failed", result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("userIds cannot be null or empty"));
    }

    @Test
    void test_sendNotification_nullMessage() {
        ApiResponse result = service.sendNotification(
                "subCat", "subType", List.of("user1"), null
        );
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
        assertEquals("Failed", result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("message cannot be null or empty"));
    }

    @Test
    void test_sendNotification_httpClientErrorException() {
        HttpClientErrorException ex = new HttpClientErrorException(
                HttpStatus.BAD_REQUEST, "Bad Request"
        );

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(ex);

        ApiResponse result = service.sendNotification(
                "subCat", "subType", List.of("user1"), Map.of("msg", "hello")
        );

        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
        assertEquals("Failed", result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("Client error:"));
    }

    @Test
    void test_sendNotification_unexpectedException() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Some error"));

        ApiResponse result = service.sendNotification(
                "subCat", "subType", List.of("user1"), Map.of("msg", "hello")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getResponseCode());
        assertEquals("Failed", result.getParams().getStatus());
        assertTrue(result.getParams().getErrMsg().contains("Unexpected error: Some error"));
    }

    @Test
    void test_triggerNotification_success() {
        com.fasterxml.jackson.databind.node.ObjectNode mockNode = mock(com.fasterxml.jackson.databind.node.ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(mockNode);
        
        Map<String, Object> mockResponse = new HashMap<>();
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        service.triggerNotification(
                "subCat", "subType", List.of("user1"), "title", "userName", Map.of("key", "value")
        );

        verify(objectMapper).createObjectNode();
        verify(mockNode).put("title", "title");
        verify(mockNode).put("userName", "userName");
    }

    @Test
    void test_triggerNotification_exception() {
        com.fasterxml.jackson.databind.node.ObjectNode mockNode = mock(com.fasterxml.jackson.databind.node.ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(mockNode);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Test exception"));

        service.triggerNotification(
                "subCat", "subType", List.of("user1"), "title", "userName", Map.of("key", "value")
        );

        verify(objectMapper).createObjectNode();
        verify(mockNode).put("title", "title");
        verify(mockNode).put("userName", "userName");
    }

}
