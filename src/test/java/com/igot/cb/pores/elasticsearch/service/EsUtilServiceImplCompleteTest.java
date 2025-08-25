package com.igot.cb.pores.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EsUtilServiceImplCompleteTest {

    @InjectMocks
    private EsUtilServiceImpl service;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        clearSchemaCache();
    }

    private void clearSchemaCache() throws Exception {
        Field cacheField = EsUtilServiceImpl.class.getDeclaredField("schemaCache");
        cacheField.setAccessible(true);
        ((Map<?, ?>) cacheField.get(null)).clear();
    }

    @Test
    void addDocument_exception() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("Schema error"));

        String result = service.addDocument("index", "id", Map.of(), "/schema.json");

        assertNull(result);
    }

    @Test
    void updateDocument_success() throws Exception {
        Map<String, Object> document = Map.of("field1", "value1");
        Map<String, Object> schema = Map.of("field1", "string");

        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class))).thenReturn(schema);

        IndexResponse mockResponse = mock(IndexResponse.class);
        when(mockResponse.result()).thenReturn(Result.Updated);
        when(elasticsearchClient.index(any(IndexRequest.class))).thenReturn(mockResponse);

        String result = service.updateDocument("index", "id", document, "/schema.json");

        assertEquals("updated", result);
    }

    @Test
    void updateDocument_ioException() throws Exception {
        when(objectMapper.readValue(any(InputStream.class), any(TypeReference.class)))
                .thenThrow(new IOException("Schema error"));

        // Create an Executable containing only one invocation
        Executable executable = () -> service.updateDocument("index", "id", Map.of(), "/schema.json");

        RuntimeException ex = assertThrows(RuntimeException.class, executable);
        assertEquals(RuntimeException.class.getName(), ex.getClass().getName());
        assertEquals("Errod occured while updating es index", ex.getMessage());
    }


    @Test
    void deleteDocument_success() throws Exception {
        DeleteResponse mockResponse = mock(DeleteResponse.class);
        when(mockResponse.result()).thenReturn(Result.Deleted);
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(mockResponse);
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.refresh(any(RefreshRequest.class))).thenReturn(mock(RefreshResponse.class));

        service.deleteDocument("id", "index");

        verify(elasticsearchClient).delete(any(DeleteRequest.class));
        verify(indicesClient).refresh(any(RefreshRequest.class));
    }

    @Test
    void deleteDocument_notFound() throws Exception {
        DeleteResponse mockResponse = mock(DeleteResponse.class);
        when(mockResponse.result()).thenReturn(Result.NotFound);
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(mockResponse);

        service.deleteDocument("id", "index");

        verify(elasticsearchClient).delete(any(DeleteRequest.class));
        verify(indicesClient, never()).refresh(any(RefreshRequest.class));
    }

    @Test
    void deleteDocument_exception() throws Exception {
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenThrow(new IOException("Delete error"));

        service.deleteDocument("id", "index");

        verify(elasticsearchClient).delete(any(DeleteRequest.class));
    }

    @Test
    void searchDocuments_success() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPageNumber(0);
        criteria.setPageSize(10);
        criteria.setFacets(List.of("category"));

        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotal = mock(TotalHits.class);
        Hit<Object> mockHit = mock(Hit.class);

        when(mockResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(mockTotal);
        when(mockTotal.value()).thenReturn(1L);
        when(mockHits.hits()).thenReturn(List.of(mockHit));
        when(mockHit.source()).thenReturn(Map.of("field", "value"));

        Aggregate mockAggregate = mock(Aggregate.class);
        StringTermsAggregate mockTermsAggregate = mock(StringTermsAggregate.class);
        StringTermsBucket mockBucket = mock(StringTermsBucket.class);

        when(mockResponse.aggregations()).thenReturn(Map.of("category_agg", mockAggregate));
        when(mockAggregate.isSterms()).thenReturn(true);
        when(mockAggregate.sterms()).thenReturn(mockTermsAggregate);
        when(mockTermsAggregate.buckets()).thenReturn(mock(Buckets.class));
        when(mockTermsAggregate.buckets().array()).thenReturn(List.of(mockBucket));
        when(mockBucket.key()).thenReturn(FieldValue.of("test"));
        when(mockBucket.docCount()).thenReturn(5L);

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        SearchResult result = service.searchDocuments("index", criteria, "/schema.json");

        assertNotNull(result);
        assertEquals(1, result.getData().size());
        assertEquals(1L, result.getTotalCount());
        assertNotNull(result.getFacets());
    }

    @Test
    void searchDocuments_nullCriteria() {
        assertThrows(AssertionError.class, () ->
                service.searchDocuments("index", null, "/schema.json"));
    }

    @Test
    void searchDocuments_emptyCriteria() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setFilterCriteriaMap(new HashMap<>());

        assertThrows(NullPointerException.class, () ->
                service.searchDocuments("index", criteria, "/schema.json"));
    }

    @Test
    void searchDocuments_ioException() throws Exception {
        SearchCriteria criteria = new SearchCriteria();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("Search error"));

        SearchResult result = service.searchDocuments("index", criteria, "/schema.json");

        assertNull(result);
    }

    @Test
    void searchDocuments_withSearchString() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("test search");

        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotal = mock(TotalHits.class);

        when(mockResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(mockTotal);
        when(mockTotal.value()).thenReturn(0L);
        when(mockHits.hits()).thenReturn(List.of());
        when(mockResponse.aggregations()).thenReturn(Map.of());

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        SearchResult result = service.searchDocuments("index", criteria, "/schema.json");

        assertNotNull(result);
        assertEquals(0, result.getData().size());
    }

    @Test
    void searchDocuments_withSorting() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOrderBy("createdOn");
        criteria.setOrderDirection("asc");

        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotal = mock(TotalHits.class);

        when(mockResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(mockTotal);
        when(mockTotal.value()).thenReturn(0L);
        when(mockHits.hits()).thenReturn(List.of());
        when(mockResponse.aggregations()).thenReturn(Map.of());

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        try (MockedStatic<EsUtilServiceImpl> mockedStatic = mockStatic(EsUtilServiceImpl.class)) {
            Map<String, Object> schemaMap = Map.of("createdOn", Map.of("type", "number"));
            mockedStatic.when(() -> EsUtilServiceImpl.readJsonSchema("/schema.json")).thenReturn(schemaMap);

            SearchResult result = service.searchDocuments("index", criteria, "/schema.json");

            assertNotNull(result);
        }
    }

    @Test
    void searchDocuments_withRequestedFields() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setRequestedFields(List.of("field1", "field2"));

        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotal = mock(TotalHits.class);

        when(mockResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(mockTotal);
        when(mockTotal.value()).thenReturn(0L);
        when(mockHits.hits()).thenReturn(List.of());
        when(mockResponse.aggregations()).thenReturn(Map.of());

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        SearchResult result = service.searchDocuments("index", criteria, "/schema.json");

        assertNotNull(result);
    }

    @Test
    void searchDocuments_withEmptyRequestedFields() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setRequestedFields(List.of());

        SearchResponse<Object> mockResponse = mock(SearchResponse.class);
        HitsMetadata<Object> mockHits = mock(HitsMetadata.class);
        TotalHits mockTotal = mock(TotalHits.class);

        when(mockResponse.hits()).thenReturn(mockHits);
        when(mockHits.total()).thenReturn(mockTotal);
        when(mockTotal.value()).thenReturn(0L);
        when(mockHits.hits()).thenReturn(List.of());
        when(mockResponse.aggregations()).thenReturn(Map.of());

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);

        SearchResult result = service.searchDocuments("index", criteria, "/schema.json");

        assertNotNull(result);
    }
}
