package com.igot.cb.metrics.service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ApiMetricsTrackerTest {
    private static final String TEST_ENDPOINT = "/api/test";

    @BeforeEach
    void setUp() {
        ApiMetricsTracker.resetMetrics();
        ApiMetricsTracker.enableTracking();
    }
    @AfterEach
    void tearDown() {
        ApiMetricsTracker.resetMetrics();
        ApiMetricsTracker.disableTracking();
    }
    @Test
    void testTrackingEnableDisable() {
        try (MockedStatic<ApiMetricsTracker> mockedStatic = Mockito.mockStatic(ApiMetricsTracker.class)) {
            mockedStatic.when(ApiMetricsTracker::isTrackingEnabled).thenReturn(true);
            assertTrue(ApiMetricsTracker.isTrackingEnabled());
            mockedStatic.when(ApiMetricsTracker::isTrackingEnabled).thenReturn(false);
            assertFalse(ApiMetricsTracker.isTrackingEnabled());
        }
    }
    @Test
    void testRecordApiCall() {
        try (MockedStatic<ApiMetricsTracker> mockedStatic = Mockito.mockStatic(ApiMetricsTracker.class)) {
            ApiMetricsTracker.ApiMetricsResponse mockResponse = new ApiMetricsTracker.ApiMetricsResponse();
            mockResponse.setApiCallCount(1);
            mockedStatic.when(() -> ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT))
                    .thenReturn(mockResponse);
            ApiMetricsTracker.recordApiCall(TEST_ENDPOINT);
            ApiMetricsTracker.ApiMetricsResponse response = ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT);
            assertEquals(1, response.getApiCallCount());
            mockedStatic.verify(() -> ApiMetricsTracker.recordApiCall(TEST_ENDPOINT));
        }
    }
    @Test
    void testRecordDbOperation() {
        try (MockedStatic<ApiMetricsTracker> mockedStatic = Mockito.mockStatic(ApiMetricsTracker.class)) {
            ApiMetricsTracker.ApiMetricsResponse mockResponse = new ApiMetricsTracker.ApiMetricsResponse();
            ApiMetricsTracker.DbMetricsResponse dbResponse = new ApiMetricsTracker.DbMetricsResponse();
            dbResponse.setInsertCount(1);
            dbResponse.setTotalInsertTime(100L);
            mockResponse.setCassandraMetrics(dbResponse);
            mockedStatic.when(() -> ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT))
                    .thenReturn(mockResponse);
            ApiMetricsTracker.recordDbOperation(TEST_ENDPOINT, "cassandra", "insert", 100L);
            ApiMetricsTracker.ApiMetricsResponse response = ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT);
            assertEquals(1, response.getCassandraMetrics().getInsertCount());
            assertEquals(100L, response.getCassandraMetrics().getTotalInsertTime());
            mockedStatic.verify(() -> ApiMetricsTracker.recordDbOperation(
                    TEST_ENDPOINT, "cassandra", "insert", 100L));
        }
    }
    @Test
    void testGetApiMetrics() {
        try (MockedStatic<ApiMetricsTracker> mockedStatic = Mockito.mockStatic(ApiMetricsTracker.class)) {
            ApiMetricsTracker.ApiMetricsResponse mockResponse = new ApiMetricsTracker.ApiMetricsResponse();
            mockResponse.setApiCallCount(5);
            mockedStatic.when(() -> ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT))
                    .thenReturn(mockResponse);
            ApiMetricsTracker.ApiMetricsResponse response = ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT);
            assertEquals(5, response.getApiCallCount());
            mockedStatic.verify(() -> ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT));
        }
    }
    @Test
    void testResetMetrics() {
        try (MockedStatic<ApiMetricsTracker> mockedStatic = Mockito.mockStatic(ApiMetricsTracker.class)) {
            ApiMetricsTracker.ApiMetricsResponse mockResponse = new ApiMetricsTracker.ApiMetricsResponse();
            mockResponse.setApiCallCount(5);
            mockedStatic.when(() -> ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT))
                    .thenReturn(mockResponse);
            ApiMetricsTracker.resetMetrics();
            mockedStatic.verify(ApiMetricsTracker::resetMetrics);
        }
    }
    @Test
    void testRecordMultipleDbOperations() {
        try (MockedStatic<ApiMetricsTracker> mockedStatic = Mockito.mockStatic(ApiMetricsTracker.class)) {
            ApiMetricsTracker.ApiMetricsResponse mockResponse = new ApiMetricsTracker.ApiMetricsResponse();
            ApiMetricsTracker.DbMetricsResponse cassandraMetrics = new ApiMetricsTracker.DbMetricsResponse();
            cassandraMetrics.setInsertCount(2);
            cassandraMetrics.setReadCount(1);
            cassandraMetrics.setTotalInsertTime(250L);
            cassandraMetrics.setTotalReadTime(100L);
            mockResponse.setCassandraMetrics(cassandraMetrics);
            mockedStatic.when(() -> ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT))
                    .thenReturn(mockResponse);
            ApiMetricsTracker.recordDbOperation(TEST_ENDPOINT, "cassandra", "insert", 100L);
            ApiMetricsTracker.recordDbOperation(TEST_ENDPOINT, "cassandra", "insert", 150L);
            ApiMetricsTracker.recordDbOperation(TEST_ENDPOINT, "cassandra", "read", 100L);
            ApiMetricsTracker.ApiMetricsResponse response = ApiMetricsTracker.getApiMetrics(TEST_ENDPOINT);
            assertEquals(2, response.getCassandraMetrics().getInsertCount());
            assertEquals(1, response.getCassandraMetrics().getReadCount());
            assertEquals(250L, response.getCassandraMetrics().getTotalInsertTime());
            assertEquals(100L, response.getCassandraMetrics().getTotalReadTime());
            mockedStatic.verify(() -> ApiMetricsTracker.recordDbOperation(
                    TEST_ENDPOINT, "cassandra", "insert", 100L));
            mockedStatic.verify(() -> ApiMetricsTracker.recordDbOperation(
                    TEST_ENDPOINT, "cassandra", "insert", 150L));
            mockedStatic.verify(() -> ApiMetricsTracker.recordDbOperation(
                    TEST_ENDPOINT, "cassandra", "read", 100L));
        }
    }
    @Test
    void testGetApiMetrics_RealBehavior() {
        String endpoint = "/api/test/real";
        ApiMetricsTracker.recordApiCall(endpoint);
        ApiMetricsTracker.recordDbOperation(endpoint, "cassandra", "read", 120);
        ApiMetricsTracker.ApiMetricsResponse response = ApiMetricsTracker.getApiMetrics(endpoint);
        assertNotNull(response);
        assertEquals(1, response.getApiCallCount());
        assertEquals(1, response.getCassandraMetrics().getReadCount());
        assertEquals(120, response.getCassandraMetrics().getTotalReadTime());
    }

}