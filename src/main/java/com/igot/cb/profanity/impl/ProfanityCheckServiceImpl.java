package com.igot.cb.profanity.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.discussion.repository.DiscussionAnswerPostReplyRepository;
import com.igot.cb.discussion.repository.DiscussionRepository;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.profanity.IProfanityCheckService;
import com.igot.cb.transactional.service.RequestHandlerServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the IProfanityCheckService interface that processes profanity checks
 * for discussion details by sending a request to a text moderation API.
 */
@Slf4j
@Service
public class ProfanityCheckServiceImpl implements IProfanityCheckService {
    @Autowired
    private CbServerProperties cbServerProperties;

    @Autowired
    private RequestHandlerServiceImpl requestHandlerService;

    @Autowired
    private DiscussionRepository discussionRepository;

    @Autowired
    private DiscussionAnswerPostReplyRepository discussionAnswerPostReplyRepository;

    /**
     * Processes a profanity check for a discussion by sending the discussion details
     * to a text moderation API.
     *
     * @param id the ID of the discussion to check
     * @param discussionDetailsNode the details of the discussion as an ObjectNode
     */
    @Override
    public void processProfanityCheck(String id, ObjectNode discussionDetailsNode) {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(Constants.CONTENT_TYPE, Constants.APPLICATION_JSON);
        headerMap.put(Constants.AUTHORIZATION, cbServerProperties.getCbDiscussionApiKey());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Constants.POST_ID, id);
        metadata.put(Constants.TYPE, discussionDetailsNode.get(Constants.TYPE).asText());
        if (Constants.ANSWER_POST.equalsIgnoreCase(discussionDetailsNode.get(Constants.TYPE).asText())) {
            metadata.put(Constants.PARENT_DISCUSSION_ID, discussionDetailsNode.get(Constants.PARENT_DISCUSSION_ID).asText());
        } else if (Constants.ANSWER_POST_REPLY.equalsIgnoreCase(discussionDetailsNode.get(Constants.TYPE).asText())){
            metadata.put(Constants.PARENT_DISCUSSION_ID, discussionDetailsNode.get(Constants.PARENT_DISCUSSION_ID).asText());
            metadata.put(Constants.PARENT_ANSWER_POST_ID, discussionDetailsNode.get(Constants.PARENT_ANSWER_POST_ID).asText());
        }
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(Constants.TEXT, discussionDetailsNode.get(Constants.DESCRIPTION).asText());
        requestBody.put(Constants.LANGUAGE, discussionDetailsNode.get(Constants.LANGUAGE).asText());
        requestBody.put(Constants.METADATA, metadata);
        Map<String, Object> mainRequest = new HashMap<>();
        mainRequest.put(Constants.HEADER_MAP, headerMap);
        mainRequest.put(Constants.REQUEST_BODY, requestBody);
        mainRequest.put(Constants.SERVICE_CODE, Constants.PROFANITY_CHECK);
        try {
            requestHandlerService.fetchResultUsingPost(cbServerProperties.getCbServiceRegistryBaseUrl() + "/" + cbServerProperties.getCbRegistryTextModerationApiPath(), mainRequest, headerMap);
        } catch (Exception e) {
            log.error("Exception while processing profanity check for discussion ID: {}", id, e);
            if (Constants.QUESTION.equalsIgnoreCase(discussionDetailsNode.get(Constants.TYPE).asText()) || Constants.ANSWER_POST.equalsIgnoreCase(discussionDetailsNode.get(Constants.TYPE).asText())) {
                discussionRepository.updateProfanityCheckStatusByDiscussionId(id, Constants.PROFANITY_CHECK_CALL_FAILED, false);
            } else if (Constants.ANSWER_POST_REPLY.equalsIgnoreCase(discussionDetailsNode.get(Constants.TYPE).asText())) {
                discussionAnswerPostReplyRepository.updateProfanityCheckStatusByDiscussionId(id, Constants.PROFANITY_CHECK_CALL_FAILED, false);
            }
        }
    }
}
