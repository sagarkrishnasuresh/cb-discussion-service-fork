package com.igot.cb.discussion.controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.discussion.service.DiscussionService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/discussion")
public class DiscussionController {

    @Autowired
    DiscussionService discussionService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> createDiscussion(@RequestBody JsonNode discussionDetails,
                                                           @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = discussionService.createDiscussion(discussionDetails,token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/read/{discussionId}")
    public ResponseEntity<ApiResponse> readDiscussion(@PathVariable String discussionId) {
        ApiResponse response = discussionService.readDiscussion(discussionId);
        return new ResponseEntity<>(response,response.getResponseCode());
    }

    @PostMapping("/update")
    public ResponseEntity<ApiResponse> updateDiscussion(@RequestBody JsonNode updateData,
                                                           @RequestHeader(Constants.X_AUTH_TOKEN) String token){
        ApiResponse response = discussionService.updateDiscussion(updateData,token);
        return new ResponseEntity<>(response,response.getResponseCode());
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse> searchDiscussion(@RequestBody SearchCriteria searchCriteria){
        ApiResponse response = discussionService.searchDiscussion(searchCriteria);
        return new ResponseEntity<>(response,response.getResponseCode());
    }

    @DeleteMapping("/delete/{discussionId}")
    public ResponseEntity<ApiResponse> deleteDiscussion(@PathVariable String discussionId,
                                                           @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = discussionService.deleteDiscussion(discussionId,token);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/vote")
    public ResponseEntity<ApiResponse> updateUpVote(@RequestBody Map<String,Object> updateData,
                                                       @RequestHeader(Constants.X_AUTH_TOKEN) String token){
        ApiResponse response = discussionService.updateUpVote(updateData,token);
        return new ResponseEntity<>(response,response.getResponseCode());
    }

    @PostMapping("/answerPosts")
    public ResponseEntity<ApiResponse> answerPost(@RequestBody JsonNode answerPostData,
                                                  @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = discussionService.createAnswerPost(answerPostData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
