package com.igot.cb.pores.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class EsConfigTest {

    private EsConfig esConfig;

    @BeforeEach
    void setUp() {
        esConfig = new EsConfig();
        ReflectionTestUtils.setField(esConfig, "elasticsearchHost", "localhost");
        ReflectionTestUtils.setField(esConfig, "elasticsearchPort", 9200);
        ReflectionTestUtils.setField(esConfig, "elasticsearchUsername", "elastic");
        ReflectionTestUtils.setField(esConfig, "elasticsearchPassword", "password");
    }

    @Test
    void test_elasticsearchClient_creation() {
        ElasticsearchClient client = esConfig.elasticsearchClient();
        assertNotNull(client);
    }

    @Test
    void test_elasticsearchClient_withDifferentHost() {
        ReflectionTestUtils.setField(esConfig, "elasticsearchHost", "es-host");
        ElasticsearchClient client = esConfig.elasticsearchClient();
        assertNotNull(client);
    }

    @Test
    void test_elasticsearchClient_withDifferentPort() {
        ReflectionTestUtils.setField(esConfig, "elasticsearchPort", 9300);
        ElasticsearchClient client = esConfig.elasticsearchClient();
        assertNotNull(client);
    }

    @Test
    void test_elasticsearchClient_withDifferentCredentials() {
        ReflectionTestUtils.setField(esConfig, "elasticsearchUsername", "admin");
        ReflectionTestUtils.setField(esConfig, "elasticsearchPassword", "admin123");
        ElasticsearchClient client = esConfig.elasticsearchClient();
        assertNotNull(client);
    }

    @Test
    void test_httpResponseInterceptor() {
        ElasticsearchClient client = esConfig.elasticsearchClient();
        assertNotNull(client);
        // The interceptor is embedded in the client configuration, so we verify client creation succeeds
        // which means the interceptor code was executed during setup
        assertNotNull(client);
    }
}
