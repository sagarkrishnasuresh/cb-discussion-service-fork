package com.igot.cb.pores.util;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JsonSchemaCache {
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    private JsonSchema loadSchema(String key, String schemaPath) {
        try (InputStream inputStream = new ClassPathResource(schemaPath).getInputStream()) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                    .getSchema(inputStream);
            schemaCache.put(key, schema);
            log.info("Successfully loaded schema file from path: {}", schemaPath);
            return schema;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JSON schema: " + schemaPath, e);
        }
    }

    public JsonSchema getSchema(String schemaKey) {
        JsonSchema jSchema = schemaCache.get(schemaKey);
        if (jSchema == null) {
            return loadSchema(schemaKey, PropertiesCache.getInstance().getProperty(schemaKey));
        }
        return schemaCache.get(schemaKey);
    }
}