package com.igot.cb.discussion.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.discussion.entity.DiscussionEntity;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public interface AnswerPostReplyService {
    ApiResponse createAnswerPostReply(JsonNode answerPostReplyData, String token);

    ApiResponse readAnswerPostReply(String discussionId);

    ApiResponse updateAnswerPostReply(JsonNode updateData, String token);

    ApiResponse deleteAnswerPostReply(String discussionId, String type, String token);

    ApiResponse managePost(Map<String, Object> reportData, String token, String action);

    ApiResponse getReportStatistics(Map<String, Object> reportData);

    ApiResponse migrateRecentReportedTime();

    SearchCriteria createDefaultSearchCriteria(String parentAnswerPostId, String communityId);

    SearchCriteria createSearchCriteriaWithDefaults(String parentDiscussionId, String communityId, String type);

    void updateAnswerPostReplyToAnswerPost(DiscussionEntity discussionEntity, String discussionId, String action);
}