package com.igot.cb.discussion.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.discussion.entity.DiscussionAnswerPostReplyEntity;
import com.igot.cb.discussion.entity.DiscussionEntity;
import com.igot.cb.discussion.repository.DiscussionAnswerPostReplyRepository;
import com.igot.cb.discussion.repository.DiscussionRepository;
import com.igot.cb.discussion.service.AnswerPostReplyService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.*;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;

@Service
@Slf4j
public class AnswerPostReplyServiceImpl implements AnswerPostReplyService {

    @Autowired
    private PayloadValidation payloadValidation;
    @Autowired
    private DiscussionRepository discussionRepository;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private EsUtilService esUtilService;
    @Autowired
    private CbServerProperties cbServerProperties;
    @Autowired
    private AccessTokenValidator accessTokenValidator;
    @Autowired
    private DiscussionAnswerPostReplyRepository discussionAnswerPostReplyRepository;
    @Autowired
    private CassandraOperation cassandraOperation;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RedisTemplate<String, SearchResult> redisTemplate;

    @Override
    public ApiResponse createAnswerPostReply(JsonNode answerPostDataReplyData, String token) {
        log.info("DiscussionService::createAnswerPostReply:creating answerPostReply");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.createAnswerPost");
        payloadValidation.validatePayload(Constants.ANSWER_POST_REPLY_VALIDATION_SCHEMA, answerPostDataReplyData);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || userId.equals(Constants.UNAUTHORIZED)) {
            response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        DiscussionEntity discussionEntity = discussionRepository.findById(answerPostDataReplyData.get(Constants.PARENT_ANSWER_POST_ID).asText()).orElse(null);
        if (discussionEntity == null || !discussionEntity.getIsActive()) {
            return ProjectUtil.returnErrorMsg(Constants.INVALID_PARENT_ANSWER_POST_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        JsonNode data = discussionEntity.getData();
        String type = data.get(Constants.TYPE).asText();
        if (!type.equals(Constants.ANSWER_POST)) {
            return ProjectUtil.returnErrorMsg("parentAnswerPostReplyId must be of type answerPost", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        if (data.get(Constants.STATUS).asText().equals(Constants.SUSPENDED)) {
            return ProjectUtil.returnErrorMsg(Constants.PARENT_DISCUSSION_ID_ERROR, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        if (!answerPostDataReplyData.get(Constants.COMMUNITY_ID).asText().equals(data.get(Constants.COMMUNITY_ID).asText())) {
            response.getParams().setErrMsg(Constants.INVALID_COMMUNITY_ID);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }

        try {
            ObjectNode answerPostReplyDataNode = (ObjectNode) answerPostDataReplyData;
            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(Constants.USERID, userId);
            propertyMap.put(Constants.COMMUNITY_ID, answerPostReplyDataNode.get(Constants.COMMUNITY_ID).asText());
            List<Map<String, Object>> communityDetails = cassandraOperation.getRecordsByPropertiesWithoutFiltering(Constants.KEYSPACE_SUNBIRD, Constants.USER_COMMUNITY, propertyMap, Arrays.asList(Constants.STATUS), null);
            if (communityDetails.isEmpty() || !(boolean) communityDetails.get(0).get(Constants.STATUS)) {
                DiscussionServiceUtil.createErrorResponse(response, Constants.USER_NOT_PART_OF_COMMUNITY, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }
            answerPostReplyDataNode.put(Constants.CREATED_BY, userId);
            answerPostReplyDataNode.put(Constants.VOTE_COUNT, 0);
            answerPostReplyDataNode.put(Constants.STATUS, Constants.ACTIVE);
            answerPostReplyDataNode.put(Constants.PARENT_ANSWER_POST_ID, answerPostDataReplyData.get(Constants.PARENT_ANSWER_POST_ID));

            DiscussionAnswerPostReplyEntity jsonNodeEntity = new DiscussionAnswerPostReplyEntity();

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            UUID id = UUIDs.timeBased();
            answerPostReplyDataNode.put(Constants.DISCUSSION_ID, String.valueOf(id));
            jsonNodeEntity.setDiscussionId(String.valueOf(id));
            jsonNodeEntity.setCreatedOn(currentTime);
            answerPostReplyDataNode.put(Constants.CREATED_ON, DiscussionServiceUtil.getFormattedCurrentTime(currentTime));
            answerPostReplyDataNode.put(Constants.UPDATED_ON, DiscussionServiceUtil.getFormattedCurrentTime(currentTime));
            jsonNodeEntity.setIsActive(true);
            answerPostReplyDataNode.put(Constants.IS_ACTIVE, true);
            jsonNodeEntity.setData(answerPostReplyDataNode);
            jsonNodeEntity.setCreatedOn(currentTime);
            jsonNodeEntity.setUpdatedOn(currentTime);
            discussionAnswerPostReplyRepository.save(jsonNodeEntity);

            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll(answerPostReplyDataNode);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, String.valueOf(id), map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + id, jsonNode);
            updateAnswerPostReplyToAnswerPost(discussionEntity, String.valueOf(id), Constants.INCREMENT);
            redisTemplate.opsForValue()
                    .getAndDelete(DiscussionServiceUtil.generateRedisJwtTokenKey(createDefaultSearchCriteria(
                            answerPostReplyDataNode.get(Constants.PARENT_ANSWER_POST_ID).asText(),
                            answerPostReplyDataNode.get(Constants.COMMUNITY_ID).asText())));
            redisTemplate.opsForValue()
                    .getAndDelete(DiscussionServiceUtil.generateRedisJwtTokenKey(createSearchCriteriaWithDefaults(
                            answerPostReplyDataNode.get(Constants.PARENT_DISCUSSION_ID).asText(),
                            answerPostReplyDataNode.get(Constants.COMMUNITY_ID).asText(),
                            Constants.ANSWER_POST)));
            log.info("AnswerPostReply post created successfully");
            map.put(Constants.CREATED_ON, currentTime);
            response.setResponseCode(HttpStatus.CREATED);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResult(map);
        } catch (Exception e) {
            log.error("Failed to create AnswerPost: {}", e.getMessage(), e);
            DiscussionServiceUtil.createErrorResponse(response, Constants.FAILED_TO_CREATE_ANSWER_POST_REPLY, HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }

    private SearchCriteria createDefaultSearchCriteria(String parentAnswerPostId,
                                                       String communityId) {
        SearchCriteria criteria = new SearchCriteria();
        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put(Constants.COMMUNITY_ID, communityId);
        filterMap.put(Constants.TYPE, Constants.ANSWER_POST_REPLY);
        filterMap.put(Constants.PARENT_ANSWER_POST_ID, parentAnswerPostId);
        criteria.setFilterCriteriaMap(filterMap);
        criteria.setRequestedFields(Collections.emptyList());
        criteria.setPageNumber(0);
        criteria.setPageSize(10);
        criteria.setOrderBy(Constants.CREATED_ON);
        criteria.setOrderDirection(Constants.DESC);
        criteria.setFacets(Collections.emptyList());
        return criteria;
    }

    private void updateAnswerPostReplyToAnswerPost(DiscussionEntity discussionEntity, String discussionId, String action) {
        log.info("DiscussionService::updateAnswerPostReplyToAnswerPost:inside");
        JsonNode data = discussionEntity.getData();
        Set<String> answerPostReplies = new HashSet<>();

        if (data.has(Constants.ANSWER_POST_REPLIES)) {
            ArrayNode existingAnswerPostReplies = (ArrayNode) data.get(Constants.ANSWER_POST_REPLIES);
            existingAnswerPostReplies.forEach(post -> answerPostReplies.add(post.asText()));
        }
        if (Constants.INCREMENT.equals(action)) {
            answerPostReplies.add(discussionId);
        } else {
            answerPostReplies.remove(discussionId);
        }

        ArrayNode arrayNode = objectMapper.valueToTree(answerPostReplies);
        ((ObjectNode) data).put(Constants.ANSWER_POST_REPLIES, arrayNode);
        ((ObjectNode) data).put(Constants.ANSWER_POST_REPLIES_COUNT, answerPostReplies.size());

        discussionEntity.setData(data);
        DiscussionEntity savedEntity = discussionRepository.save(discussionEntity);
        log.info("DiscussionService::updateAnswerPostReplyToAnswerPost: DiscussionEntity updated successfully");
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.setAll((ObjectNode) savedEntity.getData());
        Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
        esUtilService.updateDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionEntity.getDiscussionId(), map, cbServerProperties.getElasticDiscussionJsonPath());
        cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionEntity.getDiscussionId(), jsonNode);
    }

    @Override
    public ApiResponse readAnswerPostReply(String discussionId) {
        log.info("reading readAnswerPostReply details");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.ANSWER_POST_REPLY_READ_API);
        if (StringUtils.isBlank(discussionId)) {
            log.error("AnswerPostReply not found");
            DiscussionServiceUtil.createErrorResponse(response, Constants.ID_NOT_FOUND, HttpStatus.BAD_REQUEST, Constants.FAILED);
            return response;
        }
        try {
            String cachedJson = cacheService.getCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId);
            if (!StringUtils.isBlank(cachedJson)) {
                log.info("AnswerPostReply Record coming from redis cache");
                response.setMessage(Constants.SUCCESS);
                response.setResponseCode(HttpStatus.OK);
                response.setResult((Map<String, Object>) objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                }));
            } else {
                Optional<DiscussionAnswerPostReplyEntity> entityOptional = discussionAnswerPostReplyRepository.findById(discussionId);
                if (entityOptional.isPresent()) {
                    DiscussionAnswerPostReplyEntity discussionEntity = entityOptional.get();
                    cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId, discussionEntity.getData());
                    log.info("AnswerPostReply Record coming from postgres db");
                    response.setMessage(Constants.SUCCESS);
                    response.setResponseCode(HttpStatus.OK);
                    response.setResult((Map<String, Object>) objectMapper.convertValue(discussionEntity.getData(), new TypeReference<Object>() {
                    }));
                    response.getResult().put(Constants.IS_ACTIVE, discussionEntity.getIsActive());
                    response.getResult().put(Constants.CREATED_ON, discussionEntity.getCreatedOn());
                    response.getResult().put(Constants.UPDATED_ON, discussionEntity.getUpdatedOn());
                } else {
                    log.error("Invalid AnswerPostReply discussionId: {}", discussionId);
                    DiscussionServiceUtil.createErrorResponse(response, Constants.INVALID_ID, HttpStatus.NOT_FOUND, Constants.FAILED);
                    return response;
                }
            }
        } catch (Exception e) {
            log.error(" JSON for AnswerPostReplyId {}: {}", discussionId, e.getMessage(), e);
            DiscussionServiceUtil.createErrorResponse(response, "Failed to read the AnswerPostReply", HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse deleteAnswerPostReply(String discussionId, String type, String token) {
        log.info("DiscussionServiceImpl::delete AnswerPostReply");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.DELETE_ANSWER_POST_REPLY_API);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId)) {
            DiscussionServiceUtil.createErrorResponse(response, Constants.INVALID_AUTH_TOKEN, HttpStatus.BAD_REQUEST, Constants.FAILED);
            return response;
        }

        if (StringUtils.isBlank(discussionId)) {
            DiscussionServiceUtil.createErrorResponse(response, Constants.ID_NOT_FOUND, HttpStatus.BAD_REQUEST, Constants.NO_DATA_FOUND);
            return response;
        }

        try {
            Optional<DiscussionAnswerPostReplyEntity> entityOptional = discussionAnswerPostReplyRepository.findById(discussionId);
            if (!entityOptional.isPresent()) {
                DiscussionServiceUtil.createErrorResponse(response, Constants.INVALID_ID, HttpStatus.BAD_REQUEST, Constants.NO_DATA_FOUND);
                return response;
            }
            DiscussionAnswerPostReplyEntity jasonEntity = entityOptional.get();
            if (Boolean.FALSE.equals(jasonEntity.getIsActive())) {
                log.info("AnswerPostReply is already inactive.");
                DiscussionServiceUtil.createErrorResponse(response, Constants.DISCUSSION_IS_INACTIVE, HttpStatus.ALREADY_REPORTED, Constants.SUCCESS);
                return response;
            }
            JsonNode data = jasonEntity.getData();
            if (!type.equals(data.get(Constants.TYPE).asText())) {
                DiscussionServiceUtil.createErrorResponse(response, Constants.INVALID_TYPE + type, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            jasonEntity.setIsActive(false);
            jasonEntity.setUpdatedOn(currentTime);
            ((ObjectNode) data).put(Constants.IS_ACTIVE, false);
            ((ObjectNode) data).put(Constants.UPDATED_ON, String.valueOf(currentTime));
            jasonEntity.setData(data);
            jasonEntity.setDiscussionId(discussionId);
            jasonEntity.setUpdatedOn(currentTime);
            discussionAnswerPostReplyRepository.save(jasonEntity);
            Map<String, Object> map = objectMapper.convertValue(data, Map.class);
            map.put(Constants.IS_ACTIVE, false);
            DiscussionEntity discussionEntity = discussionRepository.findById(data.get(Constants.PARENT_ANSWER_POST_ID).asText()).orElse(null);
            updateAnswerPostReplyToAnswerPost(discussionEntity, discussionId, Constants.DECREMENT);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionId, map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId, data);
            log.info("AnswerPostReply details deleted successfully");
            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.DELETED_SUCCESSFULLY);
            response.getParams().setStatus(Constants.SUCCESS);
            redisTemplate.opsForValue().getAndDelete(DiscussionServiceUtil.generateRedisJwtTokenKey(createDefaultSearchCriteria(data.get(Constants.PARENT_ANSWER_POST_ID).asText(), data.get(Constants.COMMUNITY_ID).asText())));
            redisTemplate.opsForValue()
                    .getAndDelete(DiscussionServiceUtil.generateRedisJwtTokenKey(createSearchCriteriaWithDefaults(
                            data.get(Constants.PARENT_DISCUSSION_ID).asText(),
                            data.get(Constants.COMMUNITY_ID).asText(),
                            Constants.ANSWER_POST)));
            return response;
        } catch (Exception e) {
            log.error("Error while deleting discussion with ID: {}. Exception: {}", discussionId, e.getMessage(), e);
            DiscussionServiceUtil.createErrorResponse(response, Constants.FAILED_TO_DELETE_DISCUSSION, HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
    }

    @Override
    public ApiResponse updateAnswerPostReply(JsonNode answerPostReplyData, String token) {
        log.info("DiscussionService::updateAnswerPostReply:updating answerPostReply");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.ANSWER_POST_REPLY_UPDATE_API);
        payloadValidation.validatePayload(Constants.ANSWER_POST_REPLY_UPDATE_VALIDATION_SCHEMA, answerPostReplyData);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || userId.equals(Constants.UNAUTHORIZED)) {
            response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }

        DiscussionAnswerPostReplyEntity discussionAnswerPostReplyEntity = discussionAnswerPostReplyRepository.findById(answerPostReplyData.get(Constants.ANSWER_POST_REPLY_ID).asText()).orElse(null);
        if (discussionAnswerPostReplyEntity == null || !discussionAnswerPostReplyEntity.getIsActive()) {
            return ProjectUtil.returnErrorMsg(Constants.INVALID_ANSWER_POST_REPLY_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        ObjectNode data = (ObjectNode) discussionAnswerPostReplyEntity.getData();
        String type = data.get(Constants.TYPE).asText();
        if (!type.equals(Constants.ANSWER_POST_REPLY)) {
            return ProjectUtil.returnErrorMsg(Constants.INVALID_ANSWER_POST_REPLY_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        if (data.get(Constants.STATUS).asText().equals(Constants.SUSPENDED)) {
            return ProjectUtil.returnErrorMsg(Constants.POST_ALREADY_SUSPENDED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }

        try {
            ObjectNode answerPostReplyDataNode = (ObjectNode) answerPostReplyData;
            answerPostReplyDataNode.remove(Constants.ANSWER_POST_REPLY_ID);
            if (!answerPostReplyDataNode.has(Constants.IS_INITIAL_UPLOAD) || !answerPostReplyDataNode.get(Constants.IS_INITIAL_UPLOAD).asBoolean()) {
                Timestamp currentTime = new Timestamp(System.currentTimeMillis());
                answerPostReplyDataNode.put(Constants.UPDATED_ON, DiscussionServiceUtil.getFormattedCurrentTime(currentTime));
                discussionAnswerPostReplyEntity.setUpdatedOn(currentTime);
            }
            data.setAll(answerPostReplyDataNode);
            discussionAnswerPostReplyEntity.setData(data);
            discussionAnswerPostReplyRepository.save(discussionAnswerPostReplyEntity);

            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll(data);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionAnswerPostReplyEntity.getDiscussionId(), map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionAnswerPostReplyEntity.getDiscussionId(), jsonNode);
            redisTemplate.opsForValue()
                    .getAndDelete(DiscussionServiceUtil.generateRedisJwtTokenKey(createDefaultSearchCriteria(
                            data.get(Constants.PARENT_ANSWER_POST_ID).asText(),
                            data.get(Constants.COMMUNITY_ID).asText())));
            log.info("AnswerPostReply updated successfully");
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResult(map);
        } catch (Exception e) {
            log.error("Failed to update AnswerPost: {}", e.getMessage(), e);
            DiscussionServiceUtil.createErrorResponse(response, Constants.FAILED_TO_UPDATE_ANSWER_POST, HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }

    private SearchCriteria createSearchCriteriaWithDefaults(String parentDiscussionId,
                                                            String communityId,
                                                            String type) {
        SearchCriteria criteria = new SearchCriteria();

        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put(Constants.COMMUNITY_ID, communityId);
        filterMap.put(Constants.TYPE, type);
        filterMap.put(Constants.PARENT_DISCUSSION_ID, parentDiscussionId);
        criteria.setFilterCriteriaMap(filterMap);
        criteria.setRequestedFields(Collections.emptyList());
        criteria.setPageNumber(0);
        criteria.setPageSize(10);
        criteria.setOrderBy(Constants.CREATED_ON);
        criteria.setOrderDirection(Constants.DESC);
        criteria.setFacets(Collections.emptyList());
        return criteria;
    }
}
