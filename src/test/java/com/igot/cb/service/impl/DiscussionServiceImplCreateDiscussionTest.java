package com.igot.cb.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.discussion.entity.CommunityEntity;
import com.igot.cb.discussion.entity.DiscussionEntity;
import com.igot.cb.discussion.repository.CommunityEngagementRepository;
import com.igot.cb.discussion.repository.DiscussionRepository;
import com.igot.cb.discussion.service.impl.DiscussionServiceImpl;
import com.igot.cb.notificationUtill.HelperMethodService;
import com.igot.cb.notificationUtill.NotificationTriggerService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.PayloadValidation;
import com.igot.cb.producer.Producer;
import com.igot.cb.profanity.IProfanityCheckService;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static com.igot.cb.pores.util.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscussionServiceImplCreateDiscussionTest {

    @InjectMocks
    private DiscussionServiceImpl discussionService;

    @Mock
    private PayloadValidation payloadValidation;
    @Mock
    private AccessTokenValidator accessTokenValidator;
    @Mock
    private CommunityEngagementRepository communityEngagementRepository;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private DiscussionRepository discussionRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private EsUtilService esUtilService;
    @Mock
    private CacheService cacheService;
    @Mock
    private CbServerProperties cbServerProperties;
    @Mock
    private Producer producer;
    @Mock
    private NotificationTriggerService notificationTriggerService;
    @Mock
    private HelperMethodService helperMethodService;
    @Mock
    private IProfanityCheckService profanityCheckService;

    private final ObjectMapper realObjectMapper = new ObjectMapper();
    private final String validToken = "validToken";
    private final String validUserId = "user123";
    private final String validCommunityId = "community123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(discussionService, "objectMapper", objectMapper);
    }

    @Test
    void testCreateDiscussion_InvalidToken_BlankUserId() {
        // Arrange
        JsonNode discussionDetails = realObjectMapper.createObjectNode()
                .put(COMMUNITY_ID, validCommunityId);

        doNothing().when(payloadValidation).validatePayload(DISCUSSION_VALIDATION_SCHEMA, discussionDetails);
        when(accessTokenValidator.verifyUserToken(validToken)).thenReturn("");

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(INVALID_AUTH_TOKEN, response.getParams().getErrMsg());
    }

    @Test
    void testCreateDiscussion_InvalidToken_UnauthorizedUserId() {
        // Arrange
        JsonNode discussionDetails = realObjectMapper.createObjectNode()
                .put(COMMUNITY_ID, validCommunityId);

        doNothing().when(payloadValidation).validatePayload(DISCUSSION_VALIDATION_SCHEMA, discussionDetails);
        when(accessTokenValidator.verifyUserToken(validToken)).thenReturn(UNAUTHORIZED);

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(INVALID_AUTH_TOKEN, response.getParams().getErrMsg());
    }

    @Test
    void testCreateDiscussion_InvalidCommunityId() {
        // Arrange
        JsonNode discussionDetails = realObjectMapper.createObjectNode()
                .put(COMMUNITY_ID, "invalidCommunityId");

        doNothing().when(payloadValidation).validatePayload(DISCUSSION_VALIDATION_SCHEMA, discussionDetails);
        when(accessTokenValidator.verifyUserToken(validToken)).thenReturn(validUserId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive("invalidCommunityId", true))
                .thenReturn(Optional.empty());

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(INVALID_COMMUNITY_ID, response.getParams().getErrMsg());
    }

    @Test
    void testCreateDiscussion_UserNotPartOfCommunity_EmptyList() {
        // Arrange
        JsonNode discussionDetails = realObjectMapper.createObjectNode()
                .put(COMMUNITY_ID, validCommunityId);

        doNothing().when(payloadValidation).validatePayload(DISCUSSION_VALIDATION_SCHEMA, discussionDetails);
        when(accessTokenValidator.verifyUserToken(validToken)).thenReturn(validUserId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(validCommunityId, true))
                .thenReturn(Optional.of(new CommunityEntity()));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(USER_NOT_PART_OF_COMMUNITY, response.getParams().getErrMsg());
    }

    @Test
    void testCreateDiscussion_UserNotPartOfCommunity_StatusFalse() {
        // Arrange
        JsonNode discussionDetails = realObjectMapper.createObjectNode()
                .put(COMMUNITY_ID, validCommunityId);

        Map<String, Object> communityRecord = new HashMap<>();
        communityRecord.put(STATUS, false);

        doNothing().when(payloadValidation).validatePayload(DISCUSSION_VALIDATION_SCHEMA, discussionDetails);
        when(accessTokenValidator.verifyUserToken(validToken)).thenReturn(validUserId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(validCommunityId, true))
                .thenReturn(Optional.of(new CommunityEntity()));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(communityRecord));

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(USER_NOT_PART_OF_COMMUNITY, response.getParams().getErrMsg());
    }

    @Test
    void testCreateDiscussion_WithMentionedUsers_NullNode() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);
        discussionDetails.set(MENTIONED_USERS, null);

        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateDiscussion_WithMentionedUsers_EmptyArray() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);
        discussionDetails.set(MENTIONED_USERS, realObjectMapper.createArrayNode());


        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateDiscussion_WithMentionedUsers_ValidArray() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);

        ArrayNode mentionedUsers = realObjectMapper.createArrayNode();
        ObjectNode user1 = realObjectMapper.createObjectNode();
        user1.put(USER_ID_RQST, "user1");
        ObjectNode user2 = realObjectMapper.createObjectNode();
        user2.put(USER_ID_RQST, "user2");
        ObjectNode user3 = realObjectMapper.createObjectNode();
        user3.put(USER_ID_RQST, "user1"); // Duplicate
        mentionedUsers.add(user1);
        mentionedUsers.add(user2);
        mentionedUsers.add(user3);

        discussionDetails.set(MENTIONED_USERS, mentionedUsers);

        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateDiscussion_WithMentionedUsers_BlankUserId() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);

        ArrayNode mentionedUsers = realObjectMapper.createArrayNode();
        ObjectNode user1 = realObjectMapper.createObjectNode();
        user1.put(USER_ID_RQST, "");
        ObjectNode user2 = realObjectMapper.createObjectNode();
        user2.put(USER_ID_RQST, "user2");
        mentionedUsers.add(user1);
        mentionedUsers.add(user2);

        discussionDetails.set(MENTIONED_USERS, mentionedUsers);


        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateDiscussion_WithLanguage() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);
        discussionDetails.put(LANGUAGE, "en");

        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
    }


    @Test
    void testCreateDiscussion_GeneralException() {
        // Arrange
        JsonNode discussionDetails = realObjectMapper.createObjectNode()
                .put(COMMUNITY_ID, validCommunityId);

        doNothing().when(payloadValidation).validatePayload(DISCUSSION_VALIDATION_SCHEMA, discussionDetails);
        when(accessTokenValidator.verifyUserToken(validToken)).thenReturn(validUserId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(validCommunityId, true))
                .thenReturn(Optional.of(new CommunityEntity()));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(FAILED_TO_CREATE_DISCUSSION, response.getParams().getErrMsg());
        assertEquals(FAILED, response.getParams().getStatus());
    }



    @Test
    void testCreateDiscussion_MentionedUsersWithNullUserId() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);

        ArrayNode mentionedUsers = realObjectMapper.createArrayNode();
        ObjectNode user1 = realObjectMapper.createObjectNode();
        // user1 doesn't have USER_ID_RQST field, so path() returns null
        mentionedUsers.add(user1);

        discussionDetails.set(MENTIONED_USERS, mentionedUsers);


        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateDiscussion_MentionedUsersNotArray() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);
        discussionDetails.put(MENTIONED_USERS, "not-an-array"); // Not an array

        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateDiscussion_MentionedUsersArraySizeZero() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);

        ArrayNode mentionedUsers = realObjectMapper.createArrayNode();
        // Empty array - size() == 0
        discussionDetails.set(MENTIONED_USERS, mentionedUsers);

        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateDiscussion_MentionedUsersEmptyAfterFiltering() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);

        ArrayNode mentionedUsers = realObjectMapper.createArrayNode();
        ObjectNode user1 = realObjectMapper.createObjectNode();
        user1.put(USER_ID_RQST, validUserId); // Same as creator, will be filtered out
        mentionedUsers.add(user1);

        discussionDetails.set(MENTIONED_USERS, mentionedUsers);

        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
        // Should not trigger notification since filtered list is empty
        verify(notificationTriggerService, never()).triggerNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testCreateDiscussion_WithoutLanguage() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);
        // No LANGUAGE field

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(SUCCESS, response.getParams().getStatus());
        verify(profanityCheckService, never()).processProfanityCheck(any(), any());
    }

    @Test
    void testCreateDiscussion_WithNullLanguage() {
        // Arrange
        ObjectNode discussionDetails = realObjectMapper.createObjectNode();
        discussionDetails.put(COMMUNITY_ID, validCommunityId);
        discussionDetails.set(LANGUAGE, null); // Null language


        setupValidMocks();

        // Act
        ApiResponse response = discussionService.createDiscussion(discussionDetails, validToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(FAILED, response.getParams().getStatus());
        verify(profanityCheckService, never()).processProfanityCheck(any(), any());
    }

    private void setupValidMocks() {

        doNothing().when(payloadValidation).validatePayload(any(), any());
        when(accessTokenValidator.verifyUserToken(validToken)).thenReturn(validUserId);
        when(communityEngagementRepository.findByCommunityIdAndIsActive(validCommunityId, true))
                .thenReturn(Optional.of(new CommunityEntity()));

        DiscussionEntity savedEntity = new DiscussionEntity();
        savedEntity.setDiscussionId("discussion123");

        SearchResult searchResult = new SearchResult();
        searchResult.setData(new ArrayList<>());
    }
}