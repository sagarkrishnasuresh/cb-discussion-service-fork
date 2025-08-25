package com.igot.cb.discussion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class DiscussionEntityTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() throws Exception {
        DiscussionEntity entity = new DiscussionEntity();

        String discussionId = "d123";
        Timestamp createdOn = new Timestamp(System.currentTimeMillis());
        Timestamp updatedOn = new Timestamp(System.currentTimeMillis());
        Boolean isActive = true;
        Boolean isProfane = false;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"key\": \"value\"}");
        JsonNode profanityResponse = mapper.readTree("{\"profanity\": false}");

        entity.setDiscussionId(discussionId);
        entity.setData(data);
        entity.setIsActive(isActive);
        entity.setCreatedOn(createdOn);
        entity.setUpdatedOn(updatedOn);
        entity.setProfanityresponse(profanityResponse);
        entity.setIsProfane(isProfane);

        assertEquals(discussionId, entity.getDiscussionId());
        assertEquals(data, entity.getData());
        assertEquals(isActive, entity.getIsActive());
        assertEquals(createdOn, entity.getCreatedOn());
        assertEquals(updatedOn, entity.getUpdatedOn());
        assertEquals(profanityResponse, entity.getProfanityresponse());
        assertEquals(isProfane, entity.getIsProfane());
    }

    @Test
    void testAllArgsConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"foo\": \"bar\"}");
        JsonNode profanityResponse = mapper.readTree("{\"profanity\": true}");
        Timestamp createdOn = new Timestamp(System.currentTimeMillis());
        Timestamp updatedOn = new Timestamp(System.currentTimeMillis());

        DiscussionEntity entity = new DiscussionEntity(
                "d456",
                data,
                false,
                createdOn,
                updatedOn,
                profanityResponse,
                true,
                "profanity_check_status"
        );

        assertEquals("d456", entity.getDiscussionId());
        assertEquals(data, entity.getData());
        assertFalse(entity.getIsActive());
        assertEquals(createdOn, entity.getCreatedOn());
        assertEquals(updatedOn, entity.getUpdatedOn());
        assertEquals(profanityResponse, entity.getProfanityresponse());
        assertTrue(entity.getIsProfane());
    }

    @Test
    void testCustomConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"baz\": \"qux\"}");
        Timestamp createdOn = new Timestamp(System.currentTimeMillis());
        Timestamp updatedOn = new Timestamp(System.currentTimeMillis());

        DiscussionEntity entity = new DiscussionEntity(
                "d789",
                data,
                true,
                createdOn,
                updatedOn
        );

        assertEquals("d789", entity.getDiscussionId());
        assertEquals(data, entity.getData());
        assertTrue(entity.getIsActive());
        assertEquals(createdOn, entity.getCreatedOn());
        assertEquals(updatedOn, entity.getUpdatedOn());
        // The following fields should be null as they are not set by this constructor
        assertNull(entity.getProfanityresponse());
        assertNull(entity.getIsProfane());
    }
}