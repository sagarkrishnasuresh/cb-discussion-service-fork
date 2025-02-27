package com.igot.cb.discussion.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
@Service
public interface DiscussionService {
    ApiResponse createDiscussion(JsonNode discussionDetails, String token);

    ApiResponse readDiscussion(String discussionId);

    ApiResponse updateDiscussion(JsonNode updateData,String token);

    ApiResponse searchDiscussion(SearchCriteria searchCriteria);

    ApiResponse deleteDiscussion(String discussionId,String token);

    ApiResponse createAnswerPost(JsonNode answerPostData, String token);

    ApiResponse upVote(String discussionId, String token);

    ApiResponse downVote(String discussionId, String token);

    ApiResponse report(String token, Map<String, Object> reportData);

    ApiResponse uploadFile(MultipartFile file,String communityId,String discussionId);

    ApiResponse updateAnswerPost(JsonNode updateData,String token);

    ApiResponse bookmarkDiscussion(String token, String communityId, String discussionId);

    ApiResponse unBookmarkDiscussion(String communityId, String discussionId, String token);

    ApiResponse getBookmarkedDiscussions(String token,Map<String, Object> getBookmarkedPostsData);

    ApiResponse searchDiscussionByCommunity(Map<String, Object> searchData);
}
