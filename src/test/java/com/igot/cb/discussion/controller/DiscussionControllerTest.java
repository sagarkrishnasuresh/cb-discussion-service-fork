package com.igot.cb.discussion.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.discussion.service.AnswerPostReplyService;
import com.igot.cb.discussion.service.DiscussionService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class DiscussionControllerTest {
    @InjectMocks
    private DiscussionController discussionController;

    @Mock
    private DiscussionService discussionService;

    @Mock
    private AnswerPostReplyService answerPostReplyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void testCreateDiscussion(){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", "Test Discussion");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(discussionService.createDiscussion(any(), anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = discussionController.createDiscussion(json, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testReadDiscussion(){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", "Test Discussion");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(discussionService.readDiscussion(any())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = discussionController.readDiscussion("Str123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testUpdateDiscussion(){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", "Updated");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(discussionService.updateDiscussion(any(), anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = discussionController.updateDiscussion(json, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchDiscussion(){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", "Updated");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(discussionService.searchDiscussion(any(), anyBoolean())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = discussionController.searchDiscussion(new SearchCriteria());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testDeleteDiscussion(){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", "Updated");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(discussionService.deleteDiscussion(anyString(), anyString(), anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = discussionController.deletePost("123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testAnswerPost(){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", "Updated");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(discussionService.createAnswerPost(any(), anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = discussionController.answerPost(json, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testPostLike(){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", "Updated");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(discussionService.upVote(anyString(), anyString(), anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = discussionController.postLike("123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testPostDislike(){
        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("title", "Updated");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(discussionService.downVote(anyString(), anyString(), anyString())).thenReturn(mockResponse);
        ResponseEntity<ApiResponse> response = discussionController.postDislike("123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testReport() {
        Map<String, Object> request = new HashMap<>();
        request.put("reason", "spam");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.report(anyString(), anyMap())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.report(request, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testUploadFile() {
        MultipartFile multipartFile = Mockito.mock(MultipartFile.class);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.uploadFile(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.uploadFile(multipartFile, "community123", "discussion123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testUpdateAnswerPost() {
        JsonNode jsonNode = JsonNodeFactory.instance.objectNode();
        ((ObjectNode) jsonNode).put("content", "Updated answer");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.updateAnswerPost(any(JsonNode.class), anyString())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.updateAnswerPost(jsonNode, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testBookmarkDiscussion() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.bookmarkDiscussion(anyString(), anyString(), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.bookmarkDiscussion("comm123", "disc123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testUnBookmarkDiscussion() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.unBookmarkDiscussion(anyString(), anyString(), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.unBookmarkDiscussion("comm123", "disc123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testGetBookmarkedDiscussions() {
        Map<String, Object> data = new HashMap<>();
        data.put("page", 1);

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.getBookmarkedDiscussions(anyString(), anyMap()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.getBookmarkedDiscussions(data, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testSearchDiscussionByCommunity() {
        Map<String, Object> searchData = new HashMap<>();
        searchData.put("communityId", "comm123");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.searchDiscussionByCommunity(anyMap()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.searchDiscussionByCommunity(searchData);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testDeleteAnswerPost() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.deleteDiscussion(anyString(), anyString(), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.deleteAnswerPost("disc123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testAnswerPostLike() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.upVote(anyString(), eq(Constants.ANSWER_POST), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.answerPostLike("discussion123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }
    @Test
    void testAnswerPostDislike() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.downVote(anyString(), eq(Constants.ANSWER_POST), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.answerPostDislike("discussion123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testEnrichDiscussionData() {
        Map<String, Object> request = new HashMap<>();
        request.put("filter", "recent");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.getEnrichedDiscussionData(anyMap(), anyString())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.enrichDiscussionData(request, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testGetGlobalFeed() {
        SearchCriteria criteria = new SearchCriteria(); // fill this object if needed
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.getGlobalFeed(any(SearchCriteria.class), anyString(), eq(false)))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.getGlobalFeed(criteria, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testCreateAnswerPostReply() {
        JsonNode data = JsonNodeFactory.instance.objectNode().put("text", "reply");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(answerPostReplyService.createAnswerPostReply(any(JsonNode.class), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.createAnswerPostReply(data, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testReadAnswerPostReply() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(answerPostReplyService.readAnswerPostReply(anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.readAnswerPostReply("discussion123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testDeleteAnswerPostReply() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(answerPostReplyService.deleteAnswerPostReply(anyString(), eq(Constants.ANSWER_POST_REPLY), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.deleteAnswerPostReply("discussion123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUpdateAnswerPostReply() {
        JsonNode updateData = JsonNodeFactory.instance.objectNode().put("text", "updated reply");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(answerPostReplyService.updateAnswerPostReply(any(JsonNode.class), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.updateAnswerPostReply(updateData, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testAnswerPostReplyLike() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.upVote(anyString(), eq(Constants.ANSWER_POST_REPLY), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.AnswerPostReplyLike("discussion123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testAnswerPostReplyDislike() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(discussionService.downVote(anyString(), eq(Constants.ANSWER_POST_REPLY), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.AnswerPostReplyDislike("discussion123", "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testRemovePost() {
        Map<String, Object> reportData = Map.of("postId", "123");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(answerPostReplyService.managePost(anyMap(), anyString(), eq(Constants.SUSPEND)))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.removePost(reportData, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testActivatePost() {
        Map<String, Object> reportData = Map.of("postId", "123");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(answerPostReplyService.managePost(anyMap(), anyString(), eq(Constants.ACTIVE)))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.activatePost(reportData, "token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testGetReportStatistics() {
        Map<String, Object> requestData = Map.of("type", "weekly");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(answerPostReplyService.getReportStatistics(anyMap()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.getReportStatistics(requestData);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

    @Test
    void testMigrateRecentReportedTime() {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(answerPostReplyService.migrateRecentReportedTime())
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse> response = discussionController.migrateRecentReportedTime();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockResponse, response.getBody());
    }

}