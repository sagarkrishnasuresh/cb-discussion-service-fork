package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PropertiesCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CassandraConnectionManagerImplTest {

    @Mock
    private PropertiesCache propertiesCache;

    @Mock
    private CqlSession mockSession;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void test_getConsistencyLevel_validLevel() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("LOCAL_QUORUM");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, level);
        }
    }

    @Test
    void test_getConsistencyLevel_invalidLevel() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("INVALID_LEVEL");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    @Test
    void test_getConsistencyLevel_blankLevel() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn("");

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    @Test
    void test_getConsistencyLevel_nullLevel() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                    .thenReturn(null);

            ConsistencyLevel level = invokeGetConsistencyLevel();
            assertNull(level);
        }
    }

    @Test
    void test_constructor_hostNotConfigured() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

            assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
        }
    }

    @Test
    void test_constructor_nullHost() {
        try (MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class)) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn(null);

            assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
        }
    }

    @Test
    void test_getSession_connectionException() {
        try (
                MockedStatic<PropertiesCache> staticMock = mockStatic(PropertiesCache.class);
                MockedStatic<CqlSession> sessionMock = mockStatic(CqlSession.class)
        ) {
            staticMock.when(PropertiesCache::getInstance).thenReturn(propertiesCache);
            when(propertiesCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
            when(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
            when(propertiesCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
            when(propertiesCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");
            when(propertiesCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
            sessionMock.when(CqlSession::builder).thenThrow(new RuntimeException("Connection failed"));
            assertThrows(CustomException.class, CassandraConnectionManagerImpl::new);
        }
    }

    @Test
    void test_resourceCleanUp_run() {
        CassandraConnectionManagerImpl.ResourceCleanUp cleanUp = new CassandraConnectionManagerImpl.ResourceCleanUp();
        
        assertDoesNotThrow(cleanUp::run);
    }

    @Test
    void test_registerShutdownHook() {
        try (MockedStatic<Runtime> runtimeMock = mockStatic(Runtime.class)) {
            Runtime mockRuntime = mock(Runtime.class);
            runtimeMock.when(Runtime::getRuntime).thenReturn(mockRuntime);

            CassandraConnectionManagerImpl.registerShutdownHook();

            verify(mockRuntime).addShutdownHook(any(Thread.class));
        }
    }

    private ConsistencyLevel invokeGetConsistencyLevel() {
        try {
            Method method = CassandraConnectionManagerImpl.class.getDeclaredMethod("getConsistencyLevel");
            method.setAccessible(true);
            return (ConsistencyLevel) method.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
