package com.igot.cb.discussion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class CommunityEntityTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() throws Exception {
        CommunityEntity entity = new CommunityEntity();

        String communityId = "c123";
        Timestamp createdOn = new Timestamp(System.currentTimeMillis());
        Timestamp updatedOn = new Timestamp(System.currentTimeMillis());
        String createdBy = "admin";

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree("{\"key\": \"value\"}");

        entity.setCommunityId(communityId);
        entity.setData(jsonNode);
        entity.setCreatedOn(createdOn);
        entity.setUpdatedOn(updatedOn);
        entity.setCreated_by(createdBy);
        entity.setActive(true);

        assertEquals(communityId, entity.getCommunityId());
        assertEquals(jsonNode, entity.getData());
        assertEquals(createdOn, entity.getCreatedOn());
        assertEquals(updatedOn, entity.getUpdatedOn());
        assertEquals(createdBy, entity.getCreated_by());
        assertTrue(entity.isActive());
    }

    @Test
    void testAllArgsConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree("{\"name\": \"Community\"}");
        Timestamp createdOn = new Timestamp(System.currentTimeMillis());
        Timestamp updatedOn = new Timestamp(System.currentTimeMillis());

        CommunityEntity entity = new CommunityEntity(
                "c456",
                jsonNode,
                createdOn,
                updatedOn,
                "system",
                false
        );

        assertEquals("c456", entity.getCommunityId());
        assertEquals(jsonNode, entity.getData());
        assertEquals(createdOn, entity.getCreatedOn());
        assertEquals(updatedOn, entity.getUpdatedOn());
        assertEquals("system", entity.getCreated_by());
        assertFalse(entity.isActive());
    }
}

