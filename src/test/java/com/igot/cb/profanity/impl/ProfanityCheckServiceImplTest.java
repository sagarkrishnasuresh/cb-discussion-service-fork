package com.igot.cb.profanity.impl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.discussion.repository.DiscussionAnswerPostReplyRepository;
import com.igot.cb.discussion.repository.DiscussionRepository;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.service.RequestHandlerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfanityCheckServiceImplTest {

    @Mock
    private RequestHandlerServiceImpl requestHandlerService;

    @Mock
    private CbServerProperties cbServerProperties;

    @Mock
    private DiscussionRepository discussionRepository;

    @Mock
    private DiscussionAnswerPostReplyRepository discussionAnswerPostReplyRepository;

    @InjectMocks
    private ProfanityCheckServiceImpl profanityCheckService;

    private String baseUrl;
    private String moderationPath;

    @BeforeEach
    void setup() {
        baseUrl = "http://mock-base";
        moderationPath = "moderation";
        lenient().when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        lenient().when(cbServerProperties.getCbServiceRegistryBaseUrl()).thenReturn(baseUrl);
        lenient().when(cbServerProperties.getCbRegistryTextModerationApiPath()).thenReturn(moderationPath);
    }

    private ObjectNode buildNode(String type) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Constants.DESCRIPTION, "some text");
        node.put(Constants.LANGUAGE, "en");
        node.put(Constants.TYPE, type);
        if (Constants.ANSWER_POST.equals(type) || Constants.ANSWER_POST_REPLY.equals(type)) {
            node.put(Constants.PARENT_DISCUSSION_ID, "parent-disc");
        }
        if (Constants.ANSWER_POST_REPLY.equals(type)) {
            node.put(Constants.PARENT_ANSWER_POST_ID, "parent-ans");
        }
        return node;
    }

    @Test
    void testProcessProfanityCheck_HappyPath_Question() {
        String id = UUID.randomUUID().toString();
        ObjectNode node = buildNode(Constants.QUESTION);

        profanityCheckService.processProfanityCheck(id, node);

        verify(requestHandlerService).fetchResultUsingPost(
                eq(baseUrl + "/" + moderationPath),
                anyMap(),
                anyMap()
        );
        verifyNoInteractions(discussionRepository, discussionAnswerPostReplyRepository);
    }

    @Test
    void testProcessProfanityCheck_HappyPath_AnswerPost() {
        String id = UUID.randomUUID().toString();
        ObjectNode node = buildNode(Constants.ANSWER_POST);

        profanityCheckService.processProfanityCheck(id, node);

        verify(requestHandlerService).fetchResultUsingPost(anyString(),
                argThat(arg -> {
                    Map<?, ?> outer = (Map<?, ?>) arg;
                    Map<?, ?> requestBody = (Map<?, ?>) outer.get(Constants.REQUEST_BODY);
                    Map<?, ?> metadata = (Map<?, ?>) requestBody.get(Constants.METADATA);
                    return metadata.containsKey(Constants.PARENT_DISCUSSION_ID);
                }),
                anyMap());
        verifyNoInteractions(discussionRepository, discussionAnswerPostReplyRepository);
    }

    @Test
    void testProcessProfanityCheck_HappyPath_AnswerPostReply() {
        String id = UUID.randomUUID().toString();
        ObjectNode node = buildNode(Constants.ANSWER_POST_REPLY);

        profanityCheckService.processProfanityCheck(id, node);

        verify(requestHandlerService).fetchResultUsingPost(anyString(),
                argThat(arg -> {
                    Map<?, ?> outer = (Map<?, ?>) arg;
                    Map<?, ?> requestBody = (Map<?, ?>) outer.get(Constants.REQUEST_BODY);
                    Map<?, ?> metadata = (Map<?, ?>) requestBody.get(Constants.METADATA);
                    return metadata.containsKey(Constants.PARENT_DISCUSSION_ID)
                            && metadata.containsKey(Constants.PARENT_ANSWER_POST_ID);
                }),
                anyMap());
        verifyNoInteractions(discussionRepository, discussionAnswerPostReplyRepository);
    }


    @Test
    void testProcessProfanityCheck_Exception_Question() {
        String id = "qid";
        ObjectNode node = buildNode(Constants.QUESTION);
        doThrow(new RuntimeException("service down"))
                .when(requestHandlerService).fetchResultUsingPost(anyString(), anyMap(), anyMap());

        profanityCheckService.processProfanityCheck(id, node);

        verify(discussionRepository)
                .updateProfanityCheckStatusByDiscussionId(id, Constants.PROFANITY_CHECK_CALL_FAILED, false);
    }

    @Test
    void testProcessProfanityCheck_Exception_AnswerPost() {
        String id = "aid";
        ObjectNode node = buildNode(Constants.ANSWER_POST);
        doThrow(new RuntimeException("service down"))
                .when(requestHandlerService).fetchResultUsingPost(anyString(), anyMap(), anyMap());

        profanityCheckService.processProfanityCheck(id, node);

        verify(discussionRepository)
                .updateProfanityCheckStatusByDiscussionId(id, Constants.PROFANITY_CHECK_CALL_FAILED, false);
    }

    @Test
    void testProcessProfanityCheck_Exception_AnswerPostReply() {
        String id = "rid";
        ObjectNode node = buildNode(Constants.ANSWER_POST_REPLY);
        doThrow(new RuntimeException("service down"))
                .when(requestHandlerService).fetchResultUsingPost(anyString(), anyMap(), anyMap());

        profanityCheckService.processProfanityCheck(id, node);

        verify(discussionAnswerPostReplyRepository)
                .updateProfanityCheckStatusByDiscussionId(id, Constants.PROFANITY_CHECK_CALL_FAILED, false);
    }


    @Test
    void testProcessProfanityCheck_NullDiscussionDetails_ThrowsNPE() {
        assertThrows(NullPointerException.class,
                () -> profanityCheckService.processProfanityCheck("id", null));
    }

    @Test
    void testProcessProfanityCheck_MissingDescription_ThrowsNPE() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Constants.TYPE, Constants.QUESTION);
        node.put(Constants.LANGUAGE, "en");
        assertThrows(NullPointerException.class,
                () -> profanityCheckService.processProfanityCheck("id", node));
    }

    @Test
    void testProcessProfanityCheck_MissingLanguage_ThrowsNPE() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Constants.TYPE, Constants.QUESTION);
        node.put(Constants.DESCRIPTION, "desc");
        assertThrows(NullPointerException.class,
                () -> profanityCheckService.processProfanityCheck("id", node));
    }

    @Test
    void testProcessProfanityCheck_MissingType_ThrowsNPE() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(Constants.DESCRIPTION, "desc");
        node.put(Constants.LANGUAGE, "en");
        assertThrows(NullPointerException.class,
                () -> profanityCheckService.processProfanityCheck("id", node));
    }
}
