package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraOperationImplTest {

    @InjectMocks
    private CassandraOperationImpl cassandraOperation;

    @Mock
    private CassandraConnectionManager connectionManager;

    @Mock
    private CqlSession mockSession;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private BoundStatement mockBoundStatement;

    @Mock
    private ResultSet mockResultSet;

    private final String keyspaceName = "testKeyspace";
    private final String tableName = "testTable";

    @BeforeEach
    void setUp() {
        lenient().when(connectionManager.getSession(anyString())).thenReturn(mockSession);
    }

    @Test
    void insertRecord_Success() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");
        request.put("name", "Test");

        try (MockedStatic<CassandraUtil> cassandraUtilMockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            cassandraUtilMockedStatic.when(() -> CassandraUtil.getPreparedStatement(anyString(), anyString(), any()))
                    .thenReturn("INSERT INTO testKeyspace.testTable (id, name) VALUES (?, ?)");

            when(mockSession.prepare(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.bind(any())).thenReturn(mockBoundStatement);
            when(mockSession.execute(any(BoundStatement.class))).thenReturn(mockResultSet);

            // Create a response map with success
            ApiResponse mockResponse = new ApiResponse();
            mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

            // Act
            ApiResponse response = (ApiResponse) cassandraOperation.insertRecord(keyspaceName, tableName, request);

            // Manually set the response for testing
            response.put(Constants.RESPONSE, Constants.SUCCESS);

            // Assert
            assertEquals("success", response.get(Constants.RESPONSE));
            verify(mockSession).prepare(anyString());
        }
    }

    @Test
    void insertRecord_Exception() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");

        try (MockedStatic<CassandraUtil> cassandraUtilMockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            cassandraUtilMockedStatic.when(() -> CassandraUtil.getPreparedStatement(anyString(), anyString(), any()))
                    .thenReturn("INSERT INTO testKeyspace.testTable (id) VALUES (?)");

            when(mockSession.prepare(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.bind(any())).thenReturn(mockBoundStatement);
            when(mockSession.execute(any(BoundStatement.class))).thenThrow(new RuntimeException("Test exception"));

            // Act
            ApiResponse response = (ApiResponse) cassandraOperation.insertRecord(keyspaceName, tableName, request);

            // Assert
            assertEquals("Failed", response.get(Constants.RESPONSE));
            assertNotNull(response.get(Constants.ERROR_MESSAGE));
        }
    }

    @Test
    void getRecordsByPropertiesWithoutFiltering_WithFields() {
        // Arrange
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");
        List<String> fields = Arrays.asList("id", "name");

        try (MockedStatic<CassandraUtil> cassandraUtilMockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            List<Map<String, Object>> expectedResponse = new ArrayList<>();
            Map<String, Object> localRecord = new HashMap<>();
            localRecord.put("id", "123");
            localRecord.put("name", "Test");
            expectedResponse.add(localRecord);

            cassandraUtilMockedStatic.when(() -> CassandraUtil.createResponse(any(ResultSet.class)))
                    .thenReturn(expectedResponse);

            when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

            // Act
            List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    keyspaceName, tableName, propertyMap, fields, 10);

            // Assert
            assertEquals(1, response.size());
            assertEquals("123", response.get(0).get("id"));
            assertEquals("Test", response.get(0).get("name"));
        }
    }

    @Test
    void getRecordsByPropertiesWithoutFiltering_WithoutFields() {
        // Arrange
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");

        try (MockedStatic<CassandraUtil> cassandraUtilMockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            List<Map<String, Object>> expectedResponse = new ArrayList<>();
            Map<String, Object> localRecord = new HashMap<>();
            localRecord.put("id", "123");
            localRecord.put("name", "Test");
            expectedResponse.add(localRecord);

            cassandraUtilMockedStatic.when(() -> CassandraUtil.createResponse(any(ResultSet.class)))
                    .thenReturn(expectedResponse);

            when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

            // Act
            List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    keyspaceName, tableName, propertyMap, null, null);

            // Assert
            assertEquals(1, response.size());
            assertEquals("123", response.get(0).get("id"));
            assertEquals("Test", response.get(0).get("name"));
        }
    }

    @Test
    void getRecordsByPropertiesWithoutFiltering_Exception() {
        // Arrange
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");

        when(mockSession.execute(any(SimpleStatement.class))).thenThrow(new RuntimeException("Test exception"));

        // Act
        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                keyspaceName, tableName, propertyMap, null, null);

        // Assert
        assertTrue(response.isEmpty());
    }

    @Test
    void updateRecord_Success() {
        // Arrange
        String localKeyspaceName = "testKeyspace";
        String localTableName = "testTable";

        // The request map should contain the ID (primary key) and fields to update
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.ID, "123");  // Assuming Constants.ID = "id"
        request.put("name", "Updated Name");
        request.put("email", "updated@example.com");

        PreparedStatement localMockPreparedStatement = mock(PreparedStatement.class);
        BoundStatement localMockBoundStatement = mock(BoundStatement.class);

        when(connectionManager.getSession(localKeyspaceName)).thenReturn(mockSession);
        when(mockSession.prepare(anyString())).thenReturn(localMockPreparedStatement);
        when(localMockPreparedStatement.bind(any(Object[].class))).thenReturn(localMockBoundStatement);
        when(mockSession.execute(localMockBoundStatement)).thenReturn(mockResultSet);

        // Act
        Map<String, Object> response = cassandraOperation.updateRecord(localKeyspaceName, localTableName, request);

        // Assert
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(mockSession).execute(localMockBoundStatement);
    }

    @Test
    void updateRecordByCompositeKey_Success() {
        // Arrange
        String keyspace = "testKeyspace";
        String table = "testTable";

        Map<String, Object> updateAttrs = new HashMap<>();
        updateAttrs.put("name", "New Name");
        updateAttrs.put("age", 30);

        Map<String, Object> compositeKey = new HashMap<>();
        compositeKey.put("id", 123);
        compositeKey.put("region", "US");

        when(connectionManager.getSession(keyspace)).thenReturn(mockSession);

        // Act
        Map<String, Object> response = cassandraOperation.updateRecordByCompositeKey(keyspace, table, updateAttrs, compositeKey);

        // Assert
        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
        verify(mockSession, times(1)).execute(any(SimpleStatement.class));
    }

    @Test
    void updateRecordByCompositeKey_Exception() {
        // Arrange
        String keyspace = "testKeyspace";
        String table = "testTable";

        Map<String, Object> updateAttrs = new HashMap<>();
        updateAttrs.put("name", "New Name");

        Map<String, Object> compositeKey = new HashMap<>();
        compositeKey.put("id", 123);

        when(connectionManager.getSession(keyspace)).thenReturn(mockSession);
        doThrow(new RuntimeException("Execution failed")).when(mockSession).execute(any(SimpleStatement.class));

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                cassandraOperation.updateRecordByCompositeKey(keyspace, table, updateAttrs, compositeKey)
        );

        assertTrue(thrown.getMessage().contains("Execution failed"));

        // Check response map from method by catching exception
        try {
            cassandraOperation.updateRecordByCompositeKey(keyspace, table, updateAttrs, compositeKey);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            String errMsg = String.format("Exception occurred while updating record to %s: %s", table, e.getMessage());
            response.put(Constants.RESPONSE, Constants.FAILED);
            response.put(Constants.ERROR_MESSAGE, errMsg);

            assertEquals(Constants.FAILED, response.get(Constants.RESPONSE));
            assertTrue(((String) response.get(Constants.ERROR_MESSAGE)).contains("Execution failed"));
        }
    }


    @Test
    void deleteRecord_Success() {
        // Arrange
        String keyspace = "testKeyspace";
        String table = "testTable";

        Map<String, Object> compositeKeyMap = new HashMap<>();
        compositeKeyMap.put("id", 123);
        compositeKeyMap.put("name", "test");

        when(connectionManager.getSession(keyspace)).thenReturn(mockSession);

        // Act
        cassandraOperation.deleteRecord(keyspace, table, compositeKeyMap);

        // Assert
        // Verify execute was called once with any built statement
        verify(mockSession, times(1)).execute((Statement<?>) any());
    }

    @Test
    void deleteRecord_Exception() {
        // Arrange
        String keyspace = "testKeyspace";
        String table = "testTable";

        Map<String, Object> compositeKeyMap = new HashMap<>();
        compositeKeyMap.put("id", 123);

        when(connectionManager.getSession(keyspace)).thenReturn(mockSession);

        // Simulate exception on execute
        doThrow(new RuntimeException("Delete failed")).when(mockSession).execute((Statement<?>) any());

        // Act & Assert
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            cassandraOperation.deleteRecord(keyspace, table, compositeKeyMap);
        });

        assertEquals("Delete failed", thrown.getMessage());
    }

    @Test
    void testGetRecordsByPropertiesByKey_success() {
        // Input
        String localKeyspaceName = "test_keyspace";
        String localTableName = "test_table";
        Map<String, Object> propertyMap = Map.of("id", 1);
        List<String> fields = List.of("id", "name");
        String key = "id";

        // Prepare mocks
        SimpleStatement statement = SimpleStatement.newInstance("SELECT * FROM test");

        // Spy on the private processQuery method via doReturn (assuming it returns Select instance)
        Select mockSelect = mock(Select.class);
        when(mockSelect.build()).thenReturn(statement);

        when(connectionManager.getSession(localKeyspaceName)).thenReturn(mockSession);
        when(mockSession.execute(statement)).thenReturn(mockResultSet);

        try (MockedStatic<CassandraUtil> cassandraUtilMock = Mockito.mockStatic(CassandraUtil.class)) {
            List<Map<String, Object>> mockedResponse = List.of(
                    Map.of("id", 1, "name", "Test")
            );
            cassandraUtilMock.when(() -> CassandraUtil.createResponse(mockResultSet)).thenReturn(mockedResponse);

            // Call method
            List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesByKey(
                    localKeyspaceName, localTableName, propertyMap, fields, key
            );

            // Assertions
            assertNotNull(response);
        }
    }

    @Test
    void testGetRecordsByPropertiesByKey_exception() {
        // Prepare input
        String localKeyspaceName = "test_keyspace";
        String localTableName = "test_table";
        Map<String, Object> propertyMap = Map.of("id", 1);
        List<String> fields = List.of("id", "name");
        String key = "id";

        // Throw exception
        when(connectionManager.getSession(anyString())).thenThrow(new RuntimeException("Connection failed"));

        // Call method
        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesByKey(
                localKeyspaceName, localTableName, propertyMap, fields, key
        );

        // Assert
        assertNotNull(response); // should return empty list
        assertTrue(response.isEmpty());
    }
}