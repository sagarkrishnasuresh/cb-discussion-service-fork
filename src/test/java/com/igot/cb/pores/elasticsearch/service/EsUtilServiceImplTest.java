package com.igot.cb.pores.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EsUtilServiceImplTest {

    @InjectMocks
    private EsUtilServiceImpl service;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
    }
    @BeforeEach
    void clearCache() throws Exception {
        Field cacheField = EsUtilServiceImpl.class.getDeclaredField("schemaCache");
        cacheField.setAccessible(true);
        ((Map<?, ?>) cacheField.get(null)).clear();
    }


    @Test
    void test_addDocument_success() throws Exception {
        Map<String, Object> document = Map.of("field1", "value1");
        Map<String, Object> schema = Map.of("field1", "string");
        
        when(objectMapper.readValue(any(InputStream.class), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(schema);
        
        IndexResponse mockResponse = mock(IndexResponse.class);
        when(mockResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Created);
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(mockResponse);

        String result = service.addDocument("index", "id", document, "/schema.json");

        assertNotNull(result);
        assertTrue(result.contains("Successfully indexed"));
    }

    @Test
    void test_addDocument_exception() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new IOException("Schema error"));

        String result = service.addDocument("index", "id", Map.of(), "/schema.json");

        assertNull(result);
    }

    @Test
    void test_updateDocument_success() throws Exception {
        Map<String, Object> document = Map.of("field1", "value1");
        Map<String, Object> schema = Map.of("field1", "string");
        
        when(objectMapper.readValue(any(InputStream.class), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(schema);
        
        IndexResponse mockResponse = mock(IndexResponse.class);
        when(mockResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Updated);
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(mockResponse);

        String result = service.updateDocument("index", "id", document, "/schema.json");

        assertEquals("updated", result);
    }

    @Test
    void test_updateDocument_ioException() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new IOException("Schema error"));

        Executable executable = () -> service.updateDocument("index", "id", Map.of(), "/schema.json");

        RuntimeException ex = assertThrows(RuntimeException.class, executable);

        assertEquals(RuntimeException.class.getName(), ex.getClass().getName());
        assertEquals("Errod occured while updating es index", ex.getMessage());

    }

    @Test
    void test_deleteDocument_success() throws Exception {
        DeleteResponse mockResponse = mock(DeleteResponse.class);
        when(mockResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.Deleted);
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(mockResponse);
        when(elasticsearchClient.indices()).thenReturn(mock(co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient.class));
        when(elasticsearchClient.indices().refresh(any(RefreshRequest.class))).thenReturn(mock(RefreshResponse.class));

        service.deleteDocument("id", "index");

        verify(elasticsearchClient).delete(any(DeleteRequest.class));
        verify(elasticsearchClient.indices()).refresh(any(RefreshRequest.class));
    }

    @Test
    void test_deleteDocument_notFound() throws Exception {
        DeleteResponse mockResponse = mock(DeleteResponse.class);
        when(mockResponse.result()).thenReturn(co.elastic.clients.elasticsearch._types.Result.NotFound);
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(mockResponse);

        service.deleteDocument("id", "index");

        verify(elasticsearchClient).delete(any(DeleteRequest.class));
    }

    @Test
    void test_deleteDocument_exception() throws Exception {
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenThrow(new IOException("Delete error"));

        service.deleteDocument("id", "index");

        verify(elasticsearchClient).delete(any(DeleteRequest.class));
    }

    @Test
    void test_searchDocuments_success() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPageNumber(0);
        criteria.setPageSize(10);
        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put("field", "value");
        criteria.setFilterCriteriaMap(filterMap);
        
        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotal = mock(TotalHits.class);
        Hit<Object> mockHit = mock(Hit.class);
        
        when(mockResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(mockTotal);
        when(mockTotal.value()).thenReturn(1L);
        when(mockHits.hits()).thenReturn(List.of(mockHit));
        when(mockHit.source()).thenReturn(Map.of("field", "value"));
        when(mockResponse.aggregations()).thenReturn(Map.of());
        
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        SearchResult result = service.searchDocuments("index", criteria, "/schema.json");

        assertNotNull(result);
        assertEquals(1, result.getData().size());
        assertEquals(1L, result.getTotalCount());
    }

    @Test
    void test_searchDocuments_nullCriteria() {
        // The method has an assertion that fails when searchRequestBuilder is null
        // This test should expect an AssertionError
        assertThrows(AssertionError.class, () -> 
            service.searchDocuments("index", null, "/schema.json"));
    }

    @Test
    void test_searchDocuments_ioException() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("Search error"));

        SearchResult result = service.searchDocuments("index", criteria, "/schema.json");

        assertNull(result);
    }

    @Test
    void test_isIndexPresent_true() throws Exception {
        GetIndexResponse mockResponse = mock(GetIndexResponse.class);
        when(elasticsearchClient.indices()).thenReturn(mock(co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient.class));
        when(elasticsearchClient.indices().get(any(GetIndexRequest.class))).thenReturn(mockResponse);

        boolean result = service.isIndexPresent("index");

        assertTrue(result);
    }

    @Test
    void test_isIndexPresent_false() throws Exception {
        when(elasticsearchClient.indices()).thenReturn(mock(co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient.class));
        when(elasticsearchClient.indices().get(any(GetIndexRequest.class))).thenThrow(new IOException("Index not found"));

        boolean result = service.isIndexPresent("index");

        assertFalse(result);
    }

    @Test
    void test_saveAll_success() throws Exception {
        JsonNode mockNode = mock(JsonNode.class);
        JsonNode mockIdNode = mock(JsonNode.class);
        when(mockNode.get("id")).thenReturn(mockIdNode);
        when(mockIdNode.asText()).thenReturn("id1");
        when(objectMapper.convertValue(mockNode, Map.class)).thenReturn(Map.of("id", "id1"));
        
        BulkResponse mockResponse = mock(BulkResponse.class);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(mockResponse);

        BulkResponse result = service.saveAll("index", List.of(mockNode));

        assertNotNull(result);
        verify(elasticsearchClient).bulk(any(BulkRequest.class));
    }

    @Test
    void test_saveAll_exception() {
        JsonNode mockNode = mock(JsonNode.class);
        when(mockNode.get("id")).thenThrow(new RuntimeException("Error"));

        Executable executable = () -> service.saveAll("index", List.of(mockNode));

        CustomException ex = assertThrows(CustomException.class, executable);
        assertEquals("Error", ex.getMessage());
    }


    @Test
    void test_buildBoolQuery_withAllKeys() {
        Map<String, Object> boolMap = new HashMap<>();
        boolMap.put(Constants.MUST, Collections.emptyList());
        boolMap.put(Constants.FILTER, Collections.emptyList());
        boolMap.put(Constants.MUST_NOT, Collections.emptyList());
        boolMap.put(Constants.SHOULD, Collections.emptyList());

        BoolQuery result = (BoolQuery) ReflectionTestUtils.invokeMethod(service, "buildBoolQuery", boolMap);

        assertNotNull(result);
        assertTrue(result.must().isEmpty());
        assertTrue(result.filter().isEmpty());
        assertTrue(result.mustNot().isEmpty());
        assertTrue(result.should().isEmpty());
    }

    @Test
    void test_buildBoolQuery_withNoKeys() {
        Map<String, Object> boolMap = new HashMap<>();

        BoolQuery result = (BoolQuery) ReflectionTestUtils.invokeMethod(service, "buildBoolQuery", boolMap);

        assertNotNull(result);
        assertTrue(result.must().isEmpty());
        assertTrue(result.filter().isEmpty());
        assertTrue(result.mustNot().isEmpty());
        assertTrue(result.should().isEmpty());
    }

    @Test
    void testReadJsonSchema_cacheHit() throws Exception {
        // Arrange
        String path = "/test-schema.json";
        Map<String, Object> expectedMap = Map.of("key", "value");

        Field cacheField = EsUtilServiceImpl.class.getDeclaredField("schemaCache");
        cacheField.setAccessible(true);
        ((Map<String, Object>) cacheField.get(null)).put(path, expectedMap);

        // Act
        Map<String, Object> result = EsUtilServiceImpl.readJsonSchema(path);

        // Assert
        assertSame(expectedMap, result); // Should return same object from cache
    }
}