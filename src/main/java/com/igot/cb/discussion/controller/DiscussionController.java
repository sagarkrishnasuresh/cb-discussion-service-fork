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
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/answerPosts")
    public ResponseEntity<ApiResponse> answerPost(@RequestBody JsonNode answerPostData,
                                                  @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = discussionService.createAnswerPost(answerPostData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/upVote/{discussionId}")
    public ResponseEntity<ApiResponse> upVote(@PathVariable String discussionId,
                                              @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = discussionService.upVote(discussionId, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/downVote/{discussionId}")
    public ResponseEntity<ApiResponse> downVote(@PathVariable String discussionId,
                                                @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = discussionService.downVote(discussionId, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/report")
    public ResponseEntity<ApiResponse> report(@RequestBody Map<String, Object> reportData,
                                               @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = discussionService.report(token, reportData);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/fileUpload/{communityId}/{discussionId}")
    public ResponseEntity<ApiResponse> uploadFile(
            @RequestParam(value = "file", required = true) MultipartFile multipartFile,
            @PathVariable(value = "communityId", required = true) String communityId,
            @PathVariable(value = "discussionId", required = true) String discussionId)  {
        ApiResponse uploadResponse = discussionService.uploadFile(multipartFile, communityId,discussionId);
        return new ResponseEntity<>(uploadResponse, uploadResponse.getResponseCode());
    }
}
