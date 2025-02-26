package com.igot.cb.pores.util;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

@Component
public class JsonSchemaCache {
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    // Inject all properties prefixed with "schemas."
    @Value("#{${schemas}}")
    private Map<String, String> schemaPaths;

    @PostConstruct
    public void initializeSchemas() {
        schemaPaths.forEach(this::loadSchema);
    }

    private void loadSchema(String key, String schemaPath) {
        try (InputStream inputStream = new ClassPathResource(schemaPath).getInputStream()) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                    .getSchema(inputStream);
            schemaCache.put(key, schema);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JSON schema: " + schemaPath, e);
        }
    }

    public JsonSchema getSchema(String schemaKey) {
        return schemaCache.get(schemaKey);
    }
}