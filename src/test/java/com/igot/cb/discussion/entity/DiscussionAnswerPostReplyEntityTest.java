package com.igot.cb.discussion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class DiscussionAnswerPostReplyEntityTest {

    @Test
    void testNoArgsConstructorAndSettersGetters() throws Exception {
        DiscussionAnswerPostReplyEntity entity = new DiscussionAnswerPostReplyEntity();

        String discussionId = "d123";
        Boolean isActive = true;
        Timestamp createdOn = new Timestamp(System.currentTimeMillis());
        Timestamp updatedOn = new Timestamp(System.currentTimeMillis());
        Boolean isProfane = false;

        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"key\": \"value\"}");
        JsonNode profanityresponse = mapper.readTree("{\"profanity\": \"none\"}");

        entity.setDiscussionId(discussionId);
        entity.setData(data);
        entity.setIsActive(isActive);
        entity.setCreatedOn(createdOn);
        entity.setUpdatedOn(updatedOn);
        entity.setProfanityresponse(profanityresponse);
        entity.setIsProfane(isProfane);

        assertEquals(discussionId, entity.getDiscussionId());
        assertEquals(data, entity.getData());
        assertEquals(isActive, entity.getIsActive());
        assertEquals(createdOn, entity.getCreatedOn());
        assertEquals(updatedOn, entity.getUpdatedOn());
        assertEquals(profanityresponse, entity.getProfanityresponse());
        assertEquals(isProfane, entity.getIsProfane());
    }

    @Test
    void testAllArgsConstructor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree("{\"answer\": \"Test\"}");
        JsonNode profanityresponse = mapper.readTree("{\"profanity\": \"low\"}");
        Timestamp createdOn = new Timestamp(System.currentTimeMillis());
        Timestamp updatedOn = new Timestamp(System.currentTimeMillis());

        DiscussionAnswerPostReplyEntity entity = new DiscussionAnswerPostReplyEntity(
                "d456",
                data,
                true,
                createdOn,
                updatedOn,
                profanityresponse,
                true,
                "profanity_check_status"
        );

        assertEquals("d456", entity.getDiscussionId());
        assertEquals(data, entity.getData());
        assertTrue(entity.getIsActive());
        assertEquals(createdOn, entity.getCreatedOn());
        assertEquals(updatedOn, entity.getUpdatedOn());
        assertEquals(profanityresponse, entity.getProfanityresponse());
        assertTrue(entity.getIsProfane());
    }
}