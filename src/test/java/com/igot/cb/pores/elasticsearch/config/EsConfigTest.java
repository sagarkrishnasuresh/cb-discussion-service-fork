package com.igot.cb.pores.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class EsConfigTest {

    private EsConfig esConfig;

    @BeforeEach
    void setUp() throws Exception {
        esConfig = new EsConfig();

        setField(esConfig, "elasticsearchHost", "localhost");
        setField(esConfig, "elasticsearchPort", 9200);
        setField(esConfig, "elasticsearchUsername", "elastic");
        setField(esConfig, "elasticsearchPassword", "password");
    }

    @Test
    void testElasticsearchClientCreation() {
        ElasticsearchClient client = esConfig.elasticsearchClient();
        assertNotNull(client);
    }

    // Helper method to set private fields using reflection
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
