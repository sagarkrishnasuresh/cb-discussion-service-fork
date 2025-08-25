package com.igot.cb.profanity.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.discussion.repository.DiscussionAnswerPostReplyRepository;
import com.igot.cb.discussion.repository.DiscussionRepository;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.profanity.IProfanityCheckService;
import com.igot.cb.transactional.service.RequestHandlerServiceImpl;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

class LanguageDetectionConsumerTest {

    @InjectMocks
    private LanguageDetectionConsumer consumer;

    @Mock
    private ObjectMapper mapper;
    @Mock
    private CbServerProperties cbServerProperties;
    @Mock
    private RequestHandlerServiceImpl requestHandlerService;
    @Mock
    private IProfanityCheckService profanityCheckService;

    @Mock
    private DiscussionRepository discussionRepository;

    @Mock
    private DiscussionAnswerPostReplyRepository discussionAnswerPostReplyRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCheckTextLanguage_success() throws Exception {
        String id = "discussion123";
        String text = "Hello world";
        String detectedLanguage = "en";
        String json = "{\"discussionId\":\"" + id + "\",\"text\":\"" + text + "\"}";

        ObjectNode node = mock(ObjectNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(idNode.asText()).thenReturn(id);
        when(node.get(Constants.DISCUSSION_ID)).thenReturn(idNode);
        JsonNode textNode = mock(JsonNode.class);
        when(textNode.asText()).thenReturn(text);
        when(node.get(Constants.DESCRIPTION)).thenReturn(textNode);
        when(mapper.readTree(json)).thenReturn(node);

        Map<String, Object> langDetectResponse = new HashMap<>();
        langDetectResponse.put(Constants.DETECTED_LANGUAGE, detectedLanguage);

        when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        when(cbServerProperties.getContentModerationServiceUrl()).thenReturn("http://service");
        when(cbServerProperties.getContentModerationLanguageDetectApiPath()).thenReturn("detect");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(langDetectResponse);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, json);

        consumer.checkTextLanguage(record);

        verify(node).put(Constants.LANGUAGE, detectedLanguage);
        verify(profanityCheckService).processProfanityCheck(id, node);
    }

    @Test
    void testCheckTextLanguage_noDetectedLanguageKey() throws Exception {
        String id = "discussion123";
        String text = "Hello world";
        String json = "{\"discussionId\":\"" + id + "\",\"text\":\"" + text + "\"}";

        ObjectNode node = mock(ObjectNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(idNode.asText()).thenReturn(id);
        when(node.get(Constants.DISCUSSION_ID)).thenReturn(idNode);
        JsonNode textNode = mock(JsonNode.class);
        when(textNode.asText()).thenReturn(text);
        when(node.get(Constants.DESCRIPTION)).thenReturn(textNode);

        JsonNode typeNode = mock(JsonNode.class);
        when(typeNode.asText()).thenReturn("QUESTION");
        when(node.get(Constants.TYPE)).thenReturn(typeNode);

        when(mapper.readTree(json)).thenReturn(node);

        Map<String, Object> langDetectResponse = new HashMap<>();
        // No DETECTED_LANGUAGE key

        when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        when(cbServerProperties.getContentModerationServiceUrl()).thenReturn("http://service");
        when(cbServerProperties.getContentModerationLanguageDetectApiPath()).thenReturn("detect");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(langDetectResponse);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, json);

        consumer.checkTextLanguage(record);

        verify(node, never()).put(eq(Constants.LANGUAGE), anyString());
        verifyNoInteractions(profanityCheckService);
    }

    @Test
    void testCheckTextLanguage_detectedLanguageNull() throws Exception {
        String id = "discussion123";
        String text = "Hello world";
        String json = "{\"discussionId\":\"" + id + "\",\"text\":\"" + text + "\"}";

        ObjectNode node = mock(ObjectNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(idNode.asText()).thenReturn(id);
        when(node.get(Constants.DISCUSSION_ID)).thenReturn(idNode);
        JsonNode textNode = mock(JsonNode.class);
        when(textNode.asText()).thenReturn(text);
        when(node.get(Constants.DESCRIPTION)).thenReturn(textNode);

        JsonNode typeNode = mock(JsonNode.class);
        when(typeNode.asText()).thenReturn("QUESTION"); // or "ANSWER_POST" or "ANSWER_POST_REPLY"
        when(node.get(Constants.TYPE)).thenReturn(typeNode);

        when(mapper.readTree(json)).thenReturn(node);

        Map<String, Object> langDetectResponse = new HashMap<>();
        langDetectResponse.put(Constants.DETECTED_LANGUAGE, null);

        when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        when(cbServerProperties.getContentModerationServiceUrl()).thenReturn("http://service");
        when(cbServerProperties.getContentModerationLanguageDetectApiPath()).thenReturn("detect");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(langDetectResponse);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, json);

        consumer.checkTextLanguage(record);

        verify(node, never()).put(eq(Constants.LANGUAGE), anyString());
        verifyNoInteractions(profanityCheckService);
    }

    @Test
    void testCheckTextLanguage_emptyValue() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, "");
        consumer.checkTextLanguage(record);
        verifyNoInteractions(mapper, profanityCheckService, requestHandlerService);
    }

    @Test
    void testCheckTextLanguage_invalidJson() throws Exception {
        String invalidJson = "{invalid";
        when(mapper.readTree(invalidJson)).thenThrow(new JsonProcessingException("error") {
        });
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, invalidJson);
        consumer.checkTextLanguage(record);
        verify(mapper).readTree(invalidJson);
        verifyNoInteractions(profanityCheckService, requestHandlerService);
    }

    @Test
    void testCheckTextLanguage_languageDetectionFailure_questionType() throws Exception {
        String id = "discussion123";
        String text = "Hello world";
        String json = "{\"discussionId\":\"" + id + "\",\"text\":\"" + text + "\"}";

        ObjectNode node = mock(ObjectNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(idNode.asText()).thenReturn(id);
        when(node.get(Constants.DISCUSSION_ID)).thenReturn(idNode);
        JsonNode textNode = mock(JsonNode.class);
        when(textNode.asText()).thenReturn(text);
        when(node.get(Constants.DESCRIPTION)).thenReturn(textNode);

        JsonNode typeNode = mock(JsonNode.class);
        when(typeNode.asText()).thenReturn("QUESTION");
        when(node.get(Constants.TYPE)).thenReturn(typeNode);

        when(mapper.readTree(json)).thenReturn(node);

        Map<String, Object> langDetectResponse = new HashMap<>();
        langDetectResponse.put(Constants.DETECTED_LANGUAGE, "");

        when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        when(cbServerProperties.getContentModerationServiceUrl()).thenReturn("http://service");
        when(cbServerProperties.getContentModerationLanguageDetectApiPath()).thenReturn("detect");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(langDetectResponse);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, json);

        consumer.checkTextLanguage(record);

        verify(discussionRepository).updateProfanityCheckStatusByDiscussionId(id, Constants.LANGUAGE_NOT_DETECTED, false);
        verify(discussionAnswerPostReplyRepository, never()).updateProfanityCheckStatusByDiscussionId(anyString(), anyString(), anyBoolean());
        verifyNoInteractions(profanityCheckService);
    }



    @Test
    void testCheckTextLanguage_languageDetectionFailure_answerPostReplyType() throws Exception {
        String id = "discussion123";
        String text = "Hello world";
        String json = "{\"discussionId\":\"" + id + "\",\"text\":\"" + text + "\"}";

        ObjectNode node = mock(ObjectNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(idNode.asText()).thenReturn(id);
        when(node.get(Constants.DISCUSSION_ID)).thenReturn(idNode);
        JsonNode textNode = mock(JsonNode.class);
        when(textNode.asText()).thenReturn(text);
        when(node.get(Constants.DESCRIPTION)).thenReturn(textNode);

        JsonNode typeNode = mock(JsonNode.class);
        when(typeNode.asText()).thenReturn("ANSWER_POST_REPLY");
        when(node.get(Constants.TYPE)).thenReturn(typeNode);

        when(mapper.readTree(json)).thenReturn(node);

        Map<String, Object> langDetectResponse = new HashMap<>();
        langDetectResponse.put(Constants.DETECTED_LANGUAGE, null);

        when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        when(cbServerProperties.getContentModerationServiceUrl()).thenReturn("http://service");
        when(cbServerProperties.getContentModerationLanguageDetectApiPath()).thenReturn("detect");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(langDetectResponse);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, json);

        consumer.checkTextLanguage(record);

        verify(node, never()).put(eq(Constants.LANGUAGE), anyString());
        verifyNoInteractions(profanityCheckService);
    }

    @Test
    void testCheckTextLanguage_languageDetectionFailure_unknownType() throws Exception {
        String id = "discussion123";
        String text = "Hello world";
        String json = "{\"discussionId\":\"" + id + "\",\"text\":\"" + text + "\"}";

        ObjectNode node = mock(ObjectNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(idNode.asText()).thenReturn(id);
        when(node.get(Constants.DISCUSSION_ID)).thenReturn(idNode);
        JsonNode textNode = mock(JsonNode.class);
        when(textNode.asText()).thenReturn(text);
        when(node.get(Constants.DESCRIPTION)).thenReturn(textNode);

        JsonNode typeNode = mock(JsonNode.class);
        when(typeNode.asText()).thenReturn("UNKNOWN_TYPE");
        when(node.get(Constants.TYPE)).thenReturn(typeNode);

        when(mapper.readTree(json)).thenReturn(node);

        Map<String, Object> langDetectResponse = new HashMap<>();
        langDetectResponse.put(Constants.DETECTED_LANGUAGE, "");

        when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        when(cbServerProperties.getContentModerationServiceUrl()).thenReturn("http://service");
        when(cbServerProperties.getContentModerationLanguageDetectApiPath()).thenReturn("detect");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(langDetectResponse);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, json);

        consumer.checkTextLanguage(record);

        verify(discussionRepository, never()).updateProfanityCheckStatusByDiscussionId(anyString(), anyString(), anyBoolean());
        verify(discussionAnswerPostReplyRepository, never()).updateProfanityCheckStatusByDiscussionId(anyString(), anyString(), anyBoolean());
        verifyNoInteractions(profanityCheckService);
    }

    @Test
    void testCheckTextLanguage_languageDetectionFailure_answerPostType() throws Exception {
        String id = "discussion123";
        String text = "Hello world";
        String json = "{\"discussionId\":\"" + id + "\",\"text\":\"" + text + "\"}";

        ObjectNode node = mock(ObjectNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(idNode.asText()).thenReturn(id);
        when(node.get(Constants.DISCUSSION_ID)).thenReturn(idNode);
        JsonNode textNode = mock(JsonNode.class);
        when(textNode.asText()).thenReturn(text);
        when(node.get(Constants.DESCRIPTION)).thenReturn(textNode);

        JsonNode typeNode = mock(JsonNode.class);
        when(typeNode.asText()).thenReturn(Constants.ANSWER_POST); // ✅ use constant
        when(node.get(Constants.TYPE)).thenReturn(typeNode);

        when(mapper.readTree(json)).thenReturn(node);

        Map<String, Object> langDetectResponse = new HashMap<>();
        langDetectResponse.put(Constants.DETECTED_LANGUAGE, ""); // triggers failure

        when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        when(cbServerProperties.getContentModerationServiceUrl()).thenReturn("http://service");
        when(cbServerProperties.getContentModerationLanguageDetectApiPath()).thenReturn("detect");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(langDetectResponse);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, json);

        consumer.checkTextLanguage(record);

        verify(discussionRepository).updateProfanityCheckStatusByDiscussionId(id, Constants.LANGUAGE_NOT_DETECTED, false);
        verify(discussionAnswerPostReplyRepository, never()).updateProfanityCheckStatusByDiscussionId(anyString(), anyString(), anyBoolean());
        verifyNoInteractions(profanityCheckService);
    }


    @Test
    void testCheckTextLanguage_languageDetectionServiceException() throws Exception {
        String id = "discussion123";
        String text = "Hello world";
        String json = "{\"discussionId\":\"" + id + "\",\"text\":\"" + text + "\"}";

        ObjectNode node = mock(ObjectNode.class);
        JsonNode idNode = mock(JsonNode.class);
        when(idNode.asText()).thenReturn(id);
        when(node.get(Constants.DISCUSSION_ID)).thenReturn(idNode);
        JsonNode textNode = mock(JsonNode.class);
        when(textNode.asText()).thenReturn(text);
        when(node.get(Constants.DESCRIPTION)).thenReturn(textNode);

        JsonNode typeNode = mock(JsonNode.class);
        when(typeNode.asText()).thenReturn("QUESTION");
        when(node.get(Constants.TYPE)).thenReturn(typeNode);

        when(mapper.readTree(json)).thenReturn(node);

        when(cbServerProperties.getCbDiscussionApiKey()).thenReturn("api-key");
        when(cbServerProperties.getContentModerationServiceUrl()).thenReturn("http://service");
        when(cbServerProperties.getContentModerationLanguageDetectApiPath()).thenReturn("detect");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenThrow(new RuntimeException("Service error"));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, null, json);

        consumer.checkTextLanguage(record);

        verify(discussionRepository).updateProfanityCheckStatusByDiscussionId(id, Constants.LANGUAGE_DETECTION_CALL_FAILED, false);
        verify(discussionAnswerPostReplyRepository, never()).updateProfanityCheckStatusByDiscussionId(anyString(), anyString(), anyBoolean());
        verifyNoInteractions(profanityCheckService);
    }

}