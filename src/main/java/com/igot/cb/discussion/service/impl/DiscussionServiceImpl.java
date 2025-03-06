package com.igot.cb.discussion.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.discussion.entity.CommunityEntity;
import com.igot.cb.discussion.entity.DiscussionEntity;
import com.igot.cb.discussion.repository.CommunityEngagementRepository;
import com.igot.cb.discussion.repository.DiscussionRepository;
import com.igot.cb.discussion.service.DiscussionService;
import com.igot.cb.metrics.service.ApiMetricsTracker;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.*;
import com.igot.cb.producer.Producer;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import scala.Option;
import java.time.LocalDate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DiscussionServiceImpl implements DiscussionService {
    private BaseStorageService storageService = null;

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
    private RedisTemplate<String, SearchResult> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CassandraOperation cassandraOperation;
    @Autowired
    private AccessTokenValidator accessTokenValidator;
    @Autowired
    private RedisTemplate<String, Object> redisTemp;

    @Autowired
    private CommunityEngagementRepository communityEngagementRepository;

    @Autowired
    private Producer producer;

    @Value("${kafka.topic.community.discusion.post.count}")
    private String communityPostCount;

    @Value("${kafka.topic.community.discusion.like.count}")
    private String communityLikeCount;

    @PostConstruct
    public void init() {
        if (storageService == null) {
            storageService = StorageServiceFactory.getStorageService(new StorageConfig(cbServerProperties.getCloudStorageTypeName(), cbServerProperties.getCloudStorageKey(), cbServerProperties.getCloudStorageSecret().replace("\\n", "\n"), Option.apply(cbServerProperties.getCloudStorageEndpoint()), Option.empty()));
        }
    }

    /**
     * Creates a new discussion based on the provided discussion details.
     *
     * @param discussionDetails The details of the discussion to be created.
     * @return A CustomResponse object containing the result of the operation.
     */
    @Override
    public ApiResponse createDiscussion(JsonNode discussionDetails, String token) {
        log.info("DiscussionService::createDiscussion:creating discussion");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.create");
        payloadValidation.validatePayload(Constants.DISCUSSION_VALIDATION_SCHEMA, discussionDetails);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || userId.equals(Constants.UNAUTHORIZED)) {
            response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        if (!validateCommunityId(discussionDetails.get(Constants.COMMUNITY_ID).asText())) {
            response.getParams().setErrMsg(Constants.INVALID_COMMUNITY_ID);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        updateMetricsApiCall(Constants.DISCUSSION_CREATE);
        try {
            ObjectNode discussionDetailsNode = (ObjectNode) discussionDetails;
            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(Constants.USERID, userId);
            propertyMap.put(Constants.COMMUNITY_ID, discussionDetailsNode.get(Constants.COMMUNITY_ID).asText());
            List<Map<String, Object>> communityDetails = cassandraOperation.getRecordsByPropertiesWithoutFiltering(Constants.KEYSPACE_SUNBIRD, Constants.USER_COMMUNITY, propertyMap, Arrays.asList(Constants.STATUS), null);
            if (communityDetails.isEmpty() || !(boolean)communityDetails.get(0).get(Constants.STATUS)) {
                createErrorResponse(response, Constants.USER_NOT_PART_OF_COMMUNITY, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }
            discussionDetailsNode.put(Constants.CREATED_BY, userId);
            discussionDetailsNode.put(Constants.UP_VOTE_COUNT, 0L);
            discussionDetailsNode.put(Constants.DOWN_VOTE_COUNT, 0L);
            discussionDetailsNode.put(Constants.STATUS, Constants.ACTIVE);

            DiscussionEntity jsonNodeEntity = new DiscussionEntity();

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            ZonedDateTime zonedDateTime = currentTime.toInstant().atZone(ZoneId.systemDefault());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);
            String formattedCurrentTime = zonedDateTime.format(formatter);

            UUID id = UUIDs.timeBased();
            discussionDetailsNode.put(Constants.DISCUSSION_ID, String.valueOf(id));
            jsonNodeEntity.setDiscussionId(String.valueOf(id));
            jsonNodeEntity.setCreatedOn(currentTime);
            discussionDetailsNode.put(Constants.CREATED_ON, formattedCurrentTime);
            jsonNodeEntity.setIsActive(true);
            discussionDetailsNode.put(Constants.IS_ACTIVE, true);
            jsonNodeEntity.setData(discussionDetailsNode);
            long postgresTime = System.currentTimeMillis();
            DiscussionEntity saveJsonEntity = discussionRepository.save(jsonNodeEntity);
            updateMetricsDbOperation(Constants.DISCUSSION_CREATE, Constants.POSTGRES, Constants.INSERT, postgresTime);
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll(discussionDetailsNode);
            Map<String, Object> map = objectMapper.convertValue(discussionDetailsNode, Map.class);

            response.setResponseCode(HttpStatus.CREATED);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResult(map);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, saveJsonEntity.getDiscussionId(), map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + saveJsonEntity.getDiscussionId(), jsonNode);
            deleteCacheByCommunity(discussionDetails.get(Constants.COMMUNITY_ID).asText());
            Map<String, String> communityObject = new HashMap<>();
            communityObject.put(Constants.COMMUNITY_ID, discussionDetails.get(Constants.COMMUNITY_ID).asText());
            communityObject.put(Constants.STATUS, Constants.INCREMENT);
            communityObject.put(Constants.TYPE, Constants.POST);
            producer.push(communityPostCount, communityObject);
        } catch (Exception e) {
            log.error("Failed to create discussion: {}", e.getMessage(), e);
            createErrorResponse(response, Constants.FAILED_TO_CREATE_DISCUSSION, HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }

    /**
     * Returns the discussion with the given id.
     *
     * @param discussionId The id of the discussion to retrieve
     * @return A CustomResponse containing the discussion's details
     */
    @Override
    public ApiResponse readDiscussion(String discussionId) {
        log.info("reading discussion details");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.read");
        if (StringUtils.isEmpty(discussionId)) {
            log.error("discussion not found");
            createErrorResponse(response, Constants.ID_NOT_FOUND, HttpStatus.BAD_REQUEST, Constants.FAILED);
            return response;
        }
        try {
            updateMetricsApiCall(Constants.DISCUSSION_READ);
            long redisTime = System.currentTimeMillis();
            String cachedJson = cacheService.getCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId);
            updateMetricsDbOperation(Constants.DISCUSSION_READ, Constants.REDIS, Constants.READ, redisTime);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("discussion Record coming from redis cache");
                response.setMessage(Constants.SUCCESS);
                response.setResponseCode(HttpStatus.OK);
                response.setResult((Map<String, Object>) objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                }));
            } else {
                long postgresTime = System.currentTimeMillis();
                Optional<DiscussionEntity> entityOptional = discussionRepository.findById(discussionId);
                updateMetricsDbOperation(Constants.DISCUSSION_READ, Constants.POSTGRES, Constants.READ, postgresTime);
                if (entityOptional.isPresent()) {
                    DiscussionEntity discussionEntity = entityOptional.get();
                    cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId, discussionEntity.getData());
                    log.info("discussion Record coming from postgres db");
                    response.setMessage(Constants.SUCCESS);
                    response.setResponseCode(HttpStatus.OK);
                    response.setResult((Map<String, Object>) objectMapper.convertValue(discussionEntity.getData(), new TypeReference<Object>() {
                    }));
                    response.getResult().put(Constants.IS_ACTIVE, discussionEntity.getIsActive());
                    response.getResult().put(Constants.CREATED_ON, discussionEntity.getCreatedOn());
                } else {
                    log.error("Invalid discussionId: {}", discussionId);
                    createErrorResponse(response, Constants.INVALID_ID, HttpStatus.NOT_FOUND, Constants.FAILED);
                    return response;
                }
            }
        } catch (Exception e) {
            log.error(" JSON for discussionId {}: {}", discussionId, e.getMessage(), e);
            createErrorResponse(response, "Failed to read the discussion", HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }


    /**
     * Updates the discussion with the given id based on the provided update data.
     *
     * @param updateData The data to be used for the update operation.
     * @return A CustomResponse object containing the result of the operation.
     */
    @Override
    public ApiResponse updateDiscussion(JsonNode updateData, String token) {
        ApiMetricsTracker.enableTracking();
        ApiResponse response = ProjectUtil.createDefaultResponse("update.Discussion");
        payloadValidation.validatePayload(Constants.DISCUSSION_UPDATE_VALIDATION_SCHEMA, updateData);
        try {
            updateMetricsApiCall(Constants.DISCUSSION_UPDATE);
            String discussionId = updateData.get(Constants.DISCUSSION_ID).asText();
            long postgresTime = System.currentTimeMillis();
            Optional<DiscussionEntity> discussionEntity = discussionRepository.findById(discussionId);
            updateMetricsDbOperation(Constants.DISCUSSION_UPDATE, Constants.POSTGRES, Constants.READ, postgresTime);
            if (!discussionEntity.isPresent()) {
                createErrorResponse(response, "Discussion not found", HttpStatus.NOT_FOUND, Constants.FAILED);
                return response;
            }
            DiscussionEntity discussionDbData = discussionEntity.get();
            if (!discussionDbData.getIsActive()) {
                createErrorResponse(response, Constants.DISCUSSION_IS_NOT_ACTIVE, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }
            ObjectNode data = (ObjectNode) discussionDbData.getData();
            ObjectNode updateDataNode = (ObjectNode) updateData;
            if(data.get(Constants.COMMUNITY_ID) != null && !data.get(Constants.COMMUNITY_ID).asText().equals(updateDataNode.get(Constants.COMMUNITY_ID).asText())) {
                createErrorResponse(response, Constants.COMMUNITY_ID_CANNOT_BE_UPDATED, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }
            String communityId = updateData.get(Constants.COMMUNITY_ID).asText();
            updateDataNode.remove(Constants.COMMUNITY_ID);
            updateDataNode.remove(Constants.DISCUSSION_ID);
            data.setAll(updateDataNode);

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            data.put(Constants.UPDATED_ON, getFormattedCurrentTime(currentTime));
            discussionDbData.setUpdatedOn(currentTime);
            discussionDbData.setData(data);
            long postgresInsertTime = System.currentTimeMillis();
            discussionRepository.save(discussionDbData);
            updateMetricsDbOperation(Constants.DISCUSSION_CREATE, Constants.POSTGRES, Constants.UPDATE_KEY, postgresInsertTime);
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll(data);

            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionDbData.getDiscussionId(), map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionDbData.getDiscussionId(), jsonNode);

            Map<String, Object> responseMap = objectMapper.convertValue(discussionDbData, new TypeReference<Map<String, Object>>() {});
            response.setResponseCode(HttpStatus.OK);
            response.setResult(responseMap);
            response.getParams().setStatus(Constants.SUCCESS);
            deleteCacheByCommunity(communityId);
            //updateCacheForFirstFivePages(communityId);
        } catch (Exception e) {
            log.error("Failed to update the discussion: ", e);
            createErrorResponse(response, "Failed to update the discussion", HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }


    @Override
    public ApiResponse searchDiscussion(SearchCriteria searchCriteria) {
        log.info("DiscussionServiceImpl::searchDiscussion");
        ApiMetricsTracker.enableTracking();
        ApiResponse response = ProjectUtil.createDefaultResponse("search.discussion");
        String cacheKey = generateRedisJwtTokenKey(searchCriteria);
        SearchResult searchResult = redisTemplate.opsForValue().get(cacheKey);
        if (searchResult != null) {
            log.info("DiscussionServiceImpl::searchDiscussion:  search result fetched from redis");
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            createSuccessResponse(response);
            return response;
        }
        String searchString = searchCriteria.getSearchString();
        if (searchString != null && !searchString.isEmpty() && searchString.length() < 3) {
            createErrorResponse(response, Constants.MINIMUM_CHARACTERS_NEEDED, HttpStatus.BAD_REQUEST, Constants.FAILED_CONST);
            return response;
        }
        try {
            if (MapUtils.isEmpty(searchCriteria.getFilterCriteriaMap())) {
                searchCriteria.setFilterCriteriaMap(new HashMap<>());
            }
            searchCriteria.getFilterCriteriaMap().put(Constants.IS_ACTIVE, true);
            searchCriteria.getFilterCriteriaMap().put(Constants.STATUS, Arrays.asList(Constants.ACTIVE,Constants.REPORTED));
            searchResult = esUtilService.searchDocuments(cbServerProperties.getDiscussionEntity(), searchCriteria, cbServerProperties.getElasticDiscussionJsonPath());
            List<Map<String, Object>> discussions = searchResult.getData();

            if (searchCriteria.getRequestedFields().contains(Constants.CREATED_BY) || searchCriteria.getRequestedFields().isEmpty()) {
                boolean isAnswerPost = false;
                if (searchCriteria.getFilterCriteriaMap().containsKey(Constants.TYPE) && Constants.ANSWER_POST.equals(searchCriteria.getFilterCriteriaMap().get(Constants.TYPE))) {
                    isAnswerPost = true;
                }
                fetchAndEnhanceDiscussions(discussions, isAnswerPost);
            }

            searchResult.setData(discussions);
            redisTemplate.opsForValue().set(cacheKey, searchResult, cbServerProperties.getSearchResultRedisTtl(), TimeUnit.SECONDS);
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            createSuccessResponse(response);
            return response;
        } catch (Exception e) {
            log.error("error while searching discussion : {} .", e.getMessage(), e);
            createErrorResponse(response, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED_CONST);
            return response;
        }
    }

    /**
     * Deletes the discussion with the given id.
     *
     * @param discussionId The id of the discussion to be deleted.
     * @return A CustomResponse object containing the result of the operation.
     */
    @Override
    public ApiResponse deleteDiscussion(String discussionId, String token) {
        log.info("DiscussionServiceImpl::delete Discussion");
        ApiResponse response = ProjectUtil.createDefaultResponse("delete.discussion");
        try {
            String userId = accessTokenValidator.verifyUserToken(token);
            if (StringUtils.isBlank(userId)) {
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                return response;
            }

            if (StringUtils.isNotEmpty(discussionId)) {
                Optional<DiscussionEntity> entityOptional = discussionRepository.findById(discussionId);
                if (entityOptional.isPresent()) {
                    DiscussionEntity jasonEntity = entityOptional.get();
                    JsonNode data = jasonEntity.getData();
                    Timestamp currentTime = new Timestamp(System.currentTimeMillis());
                    if (jasonEntity.getIsActive()) {
                        jasonEntity.setIsActive(false);
                        jasonEntity.setUpdatedOn(currentTime);
                        ((ObjectNode) data).put(Constants.IS_ACTIVE, false);
                        ((ObjectNode) data).put(Constants.UPDATED_ON, String.valueOf(currentTime));
                        jasonEntity.setData(data);
                        jasonEntity.setDiscussionId(discussionId);
                        jasonEntity.setUpdatedOn(currentTime);
                        discussionRepository.save(jasonEntity);
                        Map<String, Object> map = objectMapper.convertValue(data, Map.class);
                        map.put(Constants.IS_ACTIVE, false);
                        esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionId, map, cbServerProperties.getElasticDiscussionJsonPath());
                        cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId, data);
                        log.info("Discussion details deleted successfully");
                        response.setResponseCode(HttpStatus.OK);
                        response.setMessage(Constants.DELETED_SUCCESSFULLY);
                        response.getParams().setStatus(Constants.SUCCESS);
                        Map<String, String> communityObject = new HashMap<>();
                        communityObject.put(Constants.COMMUNITY_ID,
                            (String) map.get(Constants.COMMUNITY_ID));
                        communityObject.put(Constants.STATUS, Constants.DECREMENT);
                        if (Constants.QUESTION.equalsIgnoreCase(data.get(Constants.TYPE).asText())){
                            communityObject.put(Constants.TYPE, Constants.POST);
                        }else {
                            communityObject.put(Constants.TYPE, Constants.ANSWER_POST);
                            DiscussionEntity discussionEntity = discussionRepository.findById(
                                data.get(Constants.PARENT_DISCUSSION_ID).asText()).orElse(null);
                            if (discussionEntity != null) {
                                updateAnswerPostToDiscussion(discussionEntity, discussionId,
                                    Constants.DECREMENT);
                            }
                            redisTemplate.opsForValue().getAndDelete(
                                generateRedisJwtTokenKey(createSearchCriteriaWithDefaults(
                                    data.get(Constants.PARENT_DISCUSSION_ID).asText(),
                                    data.get(Constants.COMMUNITY_ID).asText(),
                                    Constants.ANSWER_POST)));
                        }
                        deleteCacheByCommunity((String) map.get(Constants.COMMUNITY_ID));
                        producer.push(communityPostCount, communityObject);
                        return response;
                    } else {
                        log.info("Discussion is already inactive.");
                        createErrorResponse(response, Constants.DISCUSSION_IS_INACTIVE, HttpStatus.OK, Constants.SUCCESS);
                        return response;
                    }
                } else {
                    createErrorResponse(response, Constants.INVALID_ID, HttpStatus.BAD_REQUEST, Constants.NO_DATA_FOUND);
                    return response;
                }
            }
        } catch (Exception e) {
            log.error("Error while deleting discussion with ID: {}. Exception: {}", discussionId, e.getMessage(), e);
            createErrorResponse(response, Constants.FAILED_TO_DELETE_DISCUSSION, HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }

    private ApiResponse vote(String discussionId, String token, String voteType) {
        log.info("DiscussionServiceImpl::vote - Type: {}", voteType);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.DISCUSSION_VOTE_API);
        try {
            String userId = accessTokenValidator.verifyUserToken(token);
            if (StringUtils.isEmpty(userId)) {
                createErrorResponse(response, Constants.INVALID_AUTH_TOKEN, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }

            Optional<DiscussionEntity> discussionEntity = Optional.of(discussionRepository.findById(discussionId).orElse(null));
            if (!discussionEntity.isPresent()) {
                createErrorResponse(response, Constants.DISCUSSION_NOT_FOUND, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }

            DiscussionEntity discussionDbData = discussionEntity.get();
            HashMap<String, Object> discussionData = objectMapper.convertValue(discussionDbData.getData(), HashMap.class);
            if (!discussionDbData.getIsActive()) {
                createErrorResponse(response, Constants.DISCUSSION_IS_INACTIVE, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }

            Object upVoteCountObj = discussionData.get(Constants.UP_VOTE_COUNT);
            Object downVoteCountObj = discussionData.get(Constants.DOWN_VOTE_COUNT);
            long existingUpVoteCount = (upVoteCountObj instanceof Number) ? ((Number) upVoteCountObj).longValue() : 0L;
            long existingDownVoteCount = (downVoteCountObj instanceof Number) ? ((Number) downVoteCountObj).longValue() : 0L;

            Map<String, Object> properties = new HashMap<>();
            properties.put(Constants.DISCUSSION_ID_KEY, discussionId);
            properties.put(Constants.USERID, userId);
            List<Map<String, Object>> existingResponseList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(Constants.KEYSPACE_SUNBIRD, Constants.USER_DISCUSSION_VOTES, properties, null, null);

            if (existingResponseList.isEmpty()) {
                Map<String, Object> propertyMap = new HashMap<>();
                propertyMap.put(Constants.USER_ID_RQST, userId);
                propertyMap.put(Constants.DISCUSSION_ID_KEY, discussionId);
                propertyMap.put(Constants.VOTE_TYPE, voteType);

                ApiResponse result = (ApiResponse) cassandraOperation.insertRecord(Constants.KEYSPACE_SUNBIRD, Constants.USER_DISCUSSION_VOTES, propertyMap);
                Map<String, Object> resultMap = result.getResult();
                if (!resultMap.get(Constants.RESPONSE).equals(Constants.SUCCESS)) {
                    response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return response;
                }
                if (voteType.equals(Constants.UP)) {
                    discussionData.put(Constants.UP_VOTE_COUNT, existingUpVoteCount + 1);
                    Map<String, String> communityObject = new HashMap<>();
                    communityObject.put(Constants.COMMUNITY_ID,
                         discussionDbData.getData().get(Constants.COMMUNITY_ID).asText());
                    communityObject.put(Constants.STATUS, Constants.INCREMENT);
                    communityObject.put(Constants.DISCUSSION_ID, discussionId);
                    producer.push(communityLikeCount, communityObject);
                } else {
                    discussionData.put(Constants.DOWN_VOTE_COUNT, existingDownVoteCount + 1);
                }
            } else {
                Map<String, Object> userVoteData = existingResponseList.get(0);
                if (userVoteData.get(Constants.VOTE_TYPE).equals(voteType)) {
                    createErrorResponse(response, String.format(Constants.USER_ALREADY_VOTED, voteType), HttpStatus.ALREADY_REPORTED, Constants.FAILED);
                    return response;
                }

                Map<String, Object> updateAttribute = new HashMap<>();
                updateAttribute.put(Constants.VOTE_TYPE, voteType);
                Map<String, Object> compositeKeys = new HashMap<>();
                compositeKeys.put(Constants.USER_ID_RQST, userId);
                compositeKeys.put(Constants.DISCUSSION_ID_KEY, discussionId);

                Map<String, Object> result = cassandraOperation.updateRecordByCompositeKey(Constants.KEYSPACE_SUNBIRD, Constants.USER_DISCUSSION_VOTES, updateAttribute, compositeKeys);
                if (!result.get(Constants.RESPONSE).equals(Constants.SUCCESS)) {
                    createErrorResponse(response, Constants.FAILED_TO_VOTE, HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
                    return response;
                }

                if (voteType.equals(Constants.UP)) {
                    discussionData.put(Constants.UP_VOTE_COUNT, existingUpVoteCount + 1);
                    discussionData.put(Constants.DOWN_VOTE_COUNT, existingDownVoteCount - 1);
                } else {
                    discussionData.put(Constants.UP_VOTE_COUNT, existingUpVoteCount - 1);
                    discussionData.put(Constants.DOWN_VOTE_COUNT, existingDownVoteCount + 1);
                }
            }

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            discussionDbData.setUpdatedOn(currentTime);
            JsonNode jsonNode = objectMapper.valueToTree(discussionData);
            discussionDbData.setData(jsonNode);
            discussionRepository.save(discussionDbData);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionDbData.getDiscussionId(), discussionData, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionDbData.getDiscussionId(), discussionData);
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
        } catch (Exception e) {
            log.error("Error while processing vote: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    public String generateRedisJwtTokenKey(Object requestPayload) {
        if (requestPayload != null) {
            try {
                String reqJsonString = objectMapper.writeValueAsString(requestPayload);
                return JWT.create().withClaim(Constants.REQUEST_PAYLOAD, reqJsonString).sign(Algorithm.HMAC256(Constants.JWT_SECRET_KEY));
            } catch (JsonProcessingException e) {
                log.error("Error occurred while converting json object to json string", e);
            }
        }
        return "";
    }

    public void createSuccessResponse(ApiResponse response) {
        response.setParams(new ApiRespParam());
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
    }

    public void createErrorResponse(ApiResponse response, String errorMessage, HttpStatus httpStatus, String status) {
        response.setParams(new ApiRespParam());
        response.getParams().setErrMsg(errorMessage);
        response.getParams().setStatus(status);
        response.setResponseCode(httpStatus);
    }

    public String validateUpvoteData(Map<String, Object> upVoteData) {
        StringBuffer str = new StringBuffer();
        List<String> errList = new ArrayList<>();

        if (StringUtils.isBlank((String) upVoteData.get(Constants.DISCUSSION_ID))) {
            errList.add(Constants.DISCUSSION_ID);
        }
        String voteType = (String) upVoteData.get(Constants.VOTETYPE);
        if (StringUtils.isBlank(voteType)) {
            errList.add(Constants.VOTETYPE);
        } else if (!Constants.UP.equalsIgnoreCase(voteType) && !Constants.DOWN.equalsIgnoreCase(voteType)) {
            errList.add("voteType must be either 'up' or 'down'");
        }
        if (!errList.isEmpty()) {
            str.append("Failed Due To Missing Params - ").append(errList).append(".");
        }
        return str.toString();
    }

    public List<Object> fetchDataForKeys(List<String> keys) {
        // Fetch values for all keys from Redis
        List<Object> values = redisTemp.opsForValue().multiGet(keys);

        // Create a map of key-value pairs, converting stringified JSON objects to User objects
        return keys.stream()
                .filter(key -> values.get(keys.indexOf(key)) != null) // Filter out null values
                .map(key -> {
                    String stringifiedJson = (String) values.get(keys.indexOf(key)); // Cast the value to String
                    try {
                        // Convert the stringified JSON to a User object using ObjectMapper
                        return objectMapper.readValue(stringifiedJson, Object.class); // You can map this to a specific User type if needed
                    } catch (Exception e) {
                        // Handle any exceptions during deserialization
                        e.printStackTrace();
                        return null; // Return null in case of error
                    }
                })
                .collect(Collectors.toList());
    }

    public List<Object> fetchUserFromPrimary(List<String> userIds) {
        List<Object> userList = new ArrayList<>();
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put(Constants.ID, userIds);
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> userInfoList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.USER_TABLE, propertyMap,
                Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID), null);
        updateMetricsDbOperation(Constants.DISCUSSION_SEARCH, Constants.CASSANDRA, Constants.READ, startTime);
        userList = userInfoList.stream()
                .map(userInfo -> {
                    Map<String, Object> userMap = new HashMap<>();

                    // Extract user ID and user name
                    String userId = (String) userInfo.get(Constants.ID);
                    String userName = (String) userInfo.get(Constants.FIRST_NAME);

                    userMap.put(Constants.USER_ID_KEY, userId);
                    userMap.put(Constants.FIRST_NAME_KEY, userName);

                    // Process profile details if present
                    String profileDetails = (String) userInfo.get(Constants.PROFILE_DETAILS);
                    if (StringUtils.isNotBlank(profileDetails)) {
                        try {
                            // Convert JSON profile details to a Map
                            Map<String, Object> profileDetailsMap = objectMapper.readValue(profileDetails,
                                    new TypeReference<HashMap<String, Object>>() {
                                    });

                            // Check for profile image and add to userMap if available
                            if (MapUtils.isNotEmpty(profileDetailsMap)) {
                                if (profileDetailsMap.containsKey(Constants.PROFILE_IMG) && StringUtils.isNotBlank((String) profileDetailsMap.get(Constants.PROFILE_IMG))) {
                                    userMap.put(Constants.PROFILE_IMG_KEY, (String) profileDetailsMap.get(Constants.PROFILE_IMG));
                                }
                                if (profileDetailsMap.containsKey(Constants.DESIGNATION_KEY) && StringUtils.isNotEmpty((String) profileDetailsMap.get(Constants.DESIGNATION_KEY))) {

                                    userMap.put(Constants.DESIGNATION_KEY, (String) profileDetailsMap.get(Constants.PROFILE_IMG));
                                }
                                if (profileDetailsMap.containsKey(Constants.EMPLOYMENT_DETAILS) && MapUtils.isNotEmpty(
                                        (Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)) && ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).containsKey(Constants.DEPARTMENT_KEY) && StringUtils.isNotBlank(
                                        (String) ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).get(Constants.DEPARTMENT_KEY))) {
                                    userMap.put(Constants.DEPARTMENT, (String) ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).get(Constants.DEPARTMENT_KEY));

                                }
                            }
                        } catch (JsonProcessingException e) {
                            log.error("Error occurred while converting json object to json string", e);
                        }
                    }

                    return userMap;
                })
                .collect(Collectors.toList());
        return userList;
    }

    @Override
    public ApiResponse createAnswerPost(JsonNode answerPostData, String token) {
        log.info("DiscussionService::createAnswerPost:creating answerPost");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.createAnswerPost");
        payloadValidation.validatePayload(Constants.DISCUSSION_ANSWER_POST_VALIDATION_SCHEMA, answerPostData);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || userId.equals(Constants.UNAUTHORIZED)) {
            response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        updateMetricsApiCall(Constants.DISCUSSION_ANSWER_POST);
        long postgresTime = System.currentTimeMillis();
        DiscussionEntity discussionEntity = discussionRepository.findById(answerPostData.get(Constants.PARENT_DISCUSSION_ID).asText()).orElse(null);
        updateMetricsDbOperation(Constants.DISCUSSION_ANSWER_POST, Constants.POSTGRES, Constants.READ, postgresTime);
        if (discussionEntity == null || !discussionEntity.getIsActive()) {
            return returnErrorMsg(Constants.INVALID_PARENT_DISCUSSION_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        JsonNode data = discussionEntity.getData();
        String type = data.get(Constants.TYPE).asText();
        if (type.equals(Constants.ANSWER_POST)) {
            return returnErrorMsg(Constants.PARENT_ANSWER_POST_ID_ERROR, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        if (data.get(Constants.STATUS).asText().equals(Constants.SUSPENDED)) {
            return returnErrorMsg(Constants.PARENT_DISCUSSION_ID_ERROR, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        if (!answerPostData.get(Constants.COMMUNITY_ID).asText().equals(data.get(Constants.COMMUNITY_ID).asText())) {
            response.getParams().setErrMsg(Constants.INVALID_COMMUNITY_ID);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }

        try {
            ObjectNode answerPostDataNode = (ObjectNode) answerPostData;
            Map<String, Object> propertyMap = new HashMap<>();
            propertyMap.put(Constants.USERID, userId);
            propertyMap.put(Constants.COMMUNITY_ID, answerPostDataNode.get(Constants.COMMUNITY_ID).asText());
            List<Map<String, Object>> communityDetails = cassandraOperation.getRecordsByPropertiesWithoutFiltering(Constants.KEYSPACE_SUNBIRD, Constants.USER_COMMUNITY, propertyMap, Arrays.asList(Constants.STATUS), null);
            if (communityDetails.isEmpty() || !(boolean)communityDetails.get(0).get(Constants.STATUS)) {
                createErrorResponse(response, Constants.USER_NOT_PART_OF_COMMUNITY, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }
            answerPostDataNode.put(Constants.CREATED_BY, userId);
            answerPostDataNode.put(Constants.VOTE_COUNT, 0);
            answerPostDataNode.put(Constants.STATUS, Constants.ACTIVE);
            answerPostDataNode.put(Constants.PARENT_DISCUSSION_ID, answerPostData.get(Constants.PARENT_DISCUSSION_ID));

            DiscussionEntity jsonNodeEntity = new DiscussionEntity();

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            UUID id = UUIDs.timeBased();
            answerPostDataNode.put(Constants.DISCUSSION_ID, String.valueOf(id));
            jsonNodeEntity.setDiscussionId(String.valueOf(id));
            jsonNodeEntity.setCreatedOn(currentTime);
            answerPostDataNode.put(Constants.CREATED_ON, getFormattedCurrentTime(currentTime));
            jsonNodeEntity.setIsActive(true);
            answerPostDataNode.put(Constants.IS_ACTIVE, true);
            jsonNodeEntity.setData(answerPostDataNode);
            long timer = System.currentTimeMillis();
            discussionRepository.save(jsonNodeEntity);
            updateMetricsDbOperation(Constants.DISCUSSION_ANSWER_POST, Constants.POSTGRES, Constants.INSERT, timer);

            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll(answerPostDataNode);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, String.valueOf(id), map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + String.valueOf(id), jsonNode);

            updateAnswerPostToDiscussion(discussionEntity, String.valueOf(id), Constants.INCREMENT);
            deleteCacheByCommunity(answerPostData.get(Constants.COMMUNITY_ID).asText());
            redisTemplate.opsForValue()
                .getAndDelete(generateRedisJwtTokenKey(createSearchCriteriaWithDefaults(
                    answerPostData.get(Constants.PARENT_DISCUSSION_ID).asText(),
                    answerPostData.get(Constants.COMMUNITY_ID).asText(),
                    Constants.ANSWER_POST)));
            Map<String, String> communityObject = new HashMap<>();
            communityObject.put(Constants.COMMUNITY_ID, answerPostData.get(Constants.COMMUNITY_ID).asText());
            communityObject.put(Constants.STATUS, Constants.INCREMENT);
            communityObject.put(Constants.TYPE, Constants.ANSWER_POST);
            producer.push(communityPostCount, communityObject);

            //updateCacheForFirstFivePages(answerPostData.get(Constants.COMMUNITY_ID).asText());
            log.info("AnswerPost created successfully");
            map.put(Constants.CREATED_ON, currentTime);
            response.setResponseCode(HttpStatus.CREATED);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResult(map);
        } catch (Exception e) {
            log.error("Failed to create AnswerPost: {}", e.getMessage(), e);
            createErrorResponse(response, Constants.FAILED_TO_CREATE_ANSWER_POST, HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }

    private SearchCriteria createSearchCriteriaWithDefaults(String parentDiscussionId,
        String communityId,
        String type) {
        SearchCriteria criteria = new SearchCriteria();

        // Initialize filterCriteriaMap with default and specified values
        HashMap<String, Object> filterMap = new HashMap<>();
        filterMap.put(Constants.COMMUNITY_ID, communityId);
        filterMap.put(Constants.TYPE, type);
        filterMap.put(Constants.PARENT_DISCUSSION_ID, parentDiscussionId);
        criteria.setFilterCriteriaMap(filterMap);
        // Initialize requestedFields with an empty list
        criteria.setRequestedFields(Collections.emptyList());

        // Set default pagination values
        criteria.setPageNumber(0);
        criteria.setPageSize(10);

        // Set default ordering
        criteria.setOrderBy(Constants.CREATED_ON);
        criteria.setOrderDirection(Constants.DESC);

        // Initialize facets with an empty list
        criteria.setFacets(Collections.emptyList());
        return criteria;

    }

    private void updateAnswerPostToDiscussion(DiscussionEntity discussionEntity, String discussionId, String action) {
        log.info("DiscussionService::updateAnswerPostToDiscussion:inside");
        JsonNode data = discussionEntity.getData();
        Set<String> answerPostSet = new HashSet<>();

        if (data.has(Constants.ANSWER_POSTS)) {
            ArrayNode existingAnswerPosts = (ArrayNode) data.get(Constants.ANSWER_POSTS);
            existingAnswerPosts.forEach(post -> answerPostSet.add(post.asText()));
        }
        if (Constants.INCREMENT.equals(action)) {
            answerPostSet.add(discussionId);
        } else {
            answerPostSet.remove(discussionId);
        }
        answerPostSet.add(discussionId);
        ArrayNode arrayNode = objectMapper.valueToTree(answerPostSet);
        ((ObjectNode) data).put(Constants.ANSWER_POSTS, arrayNode);
        ((ObjectNode) data).put(Constants.ANSWER_POST_COUNT, answerPostSet.size());

        discussionEntity.setData(data);
        DiscussionEntity savedEntity = discussionRepository.save(discussionEntity);
        log.info("DiscussionService::updateAnswerPostToDiscussion: Discussion entity updated successfully");

        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.setAll((ObjectNode) savedEntity.getData());
        Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);

        esUtilService.updateDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionEntity.getDiscussionId(), map, cbServerProperties.getElasticDiscussionJsonPath());
        cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionEntity.getDiscussionId(), jsonNode);
    }

    @Override
    public ApiResponse upVote(String discussionId, String token) {
        return vote(discussionId, token, Constants.UP);
    }

    @Override
    public ApiResponse downVote(String discussionId, String token) {
        return vote(discussionId, token, Constants.DOWN);
    }

    @Override
    public ApiResponse report(String token, Map<String, Object> reportData) {
        log.info("DiscussionService::report: Reporting discussion");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.report");
        String errorMsg = validateReportPayload(reportData);
        if (StringUtils.isNotEmpty(errorMsg)) {
            return returnErrorMsg(errorMsg, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }

        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || Constants.UNAUTHORIZED.equals(userId)) {
            return returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
        }

        try {
            String discussionId = (String) reportData.get(Constants.DISCUSSION_ID);
            Optional<DiscussionEntity> discussionDbData = discussionRepository.findById(discussionId);
            if (!discussionDbData.isPresent()) {
                return returnErrorMsg(Constants.DISCUSSION_NOT_FOUND, HttpStatus.NOT_FOUND, response, Constants.FAILED);
            }

            DiscussionEntity discussionEntity = discussionDbData.get();
            if (!discussionEntity.getIsActive()) {
                return returnErrorMsg(Constants.DISCUSSION_IS_INACTIVE, HttpStatus.CONFLICT, response, Constants.FAILED);
            }
            ObjectNode data = (ObjectNode) discussionEntity.getData();
            if (data.get(Constants.STATUS).asText().equals(Constants.SUSPENDED)) {
                return returnErrorMsg(Constants.DISCUSSION_SUSPENDED, HttpStatus.CONFLICT, response, Constants.FAILED);
            }

            // Check if the user has already reported the discussion
            Map<String, Object> reportCheckData = new HashMap<>();
            reportCheckData.put(Constants.USERID, userId);
            reportCheckData.put(Constants.DISCUSSION_ID, discussionId);
            List<Map<String, Object>> existingReports = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    Constants.KEYSPACE_SUNBIRD, Constants.USER_REPORTED_POSTS, reportCheckData, null, null);

            if (!existingReports.isEmpty()) {
                return returnErrorMsg("User has already reported this discussion", HttpStatus.OK, response, Constants.SUCCESS);
            }

            // Store user data in Cassandra
            Map<String, Object> userReportData = new HashMap<>();
            userReportData.put(Constants.USERID, userId);
            userReportData.put(Constants.DISCUSSION_ID, discussionId);
            if (reportData.containsKey(Constants.REPORTED_REASON)) {
                List<String> reportedReasonList = (List<String>) reportData.get(Constants.REPORTED_REASON);
                if (reportedReasonList != null && !reportedReasonList.isEmpty()) {
                    StringBuilder reasonBuilder = new StringBuilder(String.join(", ", reportedReasonList));

                    if (reportedReasonList.contains(Constants.OTHERS) && reportData.containsKey(Constants.OTHER_REASON)) {
                        reasonBuilder.append(", ").append(reportData.get(Constants.OTHER_REASON));
                    }
                    userReportData.put(Constants.REASON, reasonBuilder.toString());
                }
            }
            userReportData.put(Constants.CREATED_ON, new Timestamp(System.currentTimeMillis()));
            cassandraOperation.insertRecord(Constants.KEYSPACE_SUNBIRD, Constants.USER_REPORTED_POSTS, userReportData);
            cassandraOperation.insertRecord(Constants.KEYSPACE_SUNBIRD, Constants.POST_REPORTED_BY_USER, userReportData);

            // Update the status of the discussion in Cassandra
            String status ;
            if (cbServerProperties.isDiscussionReportHidePost()) {
                List<Map<String, Object>> reportedByUsers = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                        Constants.KEYSPACE_SUNBIRD, Constants.POST_REPORTED_BY_USER, Collections.singletonMap(Constants.DISCUSSION_ID, discussionId), null, null);
                status = reportedByUsers.size() >= cbServerProperties.getReportPostUserLimit() ? Constants.SUSPENDED : Constants.REPORTED;
            } else {
                status = Constants.REPORTED;
            }

            Map<String, Object> statusUpdateData = new HashMap<>();
            statusUpdateData.put(Constants.STATUS, status);
            ObjectNode jsonNode = objectMapper.createObjectNode();

            if (!data.get(Constants.STATUS).textValue().equals(status)) {
                data.put(Constants.STATUS, status);
            }

            ArrayNode reportedByArray;
            if (data.has(Constants.REPORTED_BY)) {
                reportedByArray = (ArrayNode) data.get(Constants.REPORTED_BY);
            } else {
                reportedByArray = objectMapper.createArrayNode();
                data.set(Constants.REPORTED_BY, reportedByArray);
            }
            reportedByArray.add(userId);
            discussionEntity.setData(data);
            discussionRepository.save(discussionEntity);
            jsonNode.setAll(data);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionId, map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId, jsonNode);
            deleteCacheByCommunity(data.get(Constants.COMMUNITY_ID).asText());
            map.put(Constants.DISCUSSION_ID, reportData.get(Constants.DISCUSSION_ID));
            response.setResult(map);
            return response;
        } catch (Exception e) {
            log.error("DiscussionService::report: Failed to report discussion", e);
            return returnErrorMsg(Constants.DISCUSSION_REPORT_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
        }
    }

    private String validateReportPayload(Map<String, Object> reportData) {
        StringBuffer errorMsg = new StringBuffer();
        List<String> errList = new ArrayList<>();

        if (reportData.containsKey(Constants.DISCUSSION_ID) && StringUtils.isBlank((String) reportData.get(Constants.DISCUSSION_ID))){
            errList.add(Constants.DISCUSSION_ID);
        }
        if (reportData.containsKey(Constants.REPORTED_REASON)) {
            Object reportedReasonObj = reportData.get(Constants.REPORTED_REASON);
            if (reportedReasonObj instanceof List) {
                List<String> reportedReasonList = (List<String>) reportedReasonObj;
                if (reportedReasonList.isEmpty()) {
                    errList.add(Constants.REPORTED_REASON);
                } else if (reportedReasonList.contains(Constants.OTHERS)) {
                    if (!reportData.containsKey(Constants.OTHER_REASON) ||
                            StringUtils.isBlank((String) reportData.get(Constants.OTHER_REASON))) {
                        errList.add(Constants.OTHER_REASON);
                    }
                }
            } else {
                errList.add(Constants.REPORTED_REASON);
            }
        }
        if (!errList.isEmpty()) {
            errorMsg.append("Failed Due To Missing Params - ").append(errList).append(".");
        }
        return errorMsg.toString();
    }

    private  ApiResponse returnErrorMsg(String error, HttpStatus type, ApiResponse response, String status) {
        response.setResponseCode(type);
        response.getParams().setErr(error);
        response.setMessage(status);
        return response;
    }


    @Override
    public ApiResponse uploadFile(MultipartFile mFile, String communityId,String discussionId) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.DISCUSSION_UPLOAD_FILE);
        if(mFile.isEmpty()){
            return returnErrorMsg(Constants.DISCUSSION_FILE_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        if(StringUtils.isBlank(discussionId)){
            return returnErrorMsg(Constants.INVALID_DISCUSSION_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        if(StringUtils.isBlank(communityId)){
            return returnErrorMsg(Constants.INVALID_COMMUNITY_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }

        File file = null;
        try {
            file = new File(System.currentTimeMillis() + "_" + mFile.getOriginalFilename());

            file.createNewFile();
            // Use try-with-resources to ensure FileOutputStream is closed
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(mFile.getBytes());
            }

            String uploadFolderPath = cbServerProperties.getDiscussionCloudFolderName() + "/" + communityId + "/" + discussionId;
            return uploadFile(file, uploadFolderPath, cbServerProperties.getDiscussionContainerName());
        } catch (Exception e) {
            log.error("Failed to upload file. Exception: ", e);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("Failed to upload file. Exception: " + e.getMessage());
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        } finally {
            if (file != null && file.exists()) {
                file.delete();
            }
        }
    }

    public ApiResponse uploadFile(File file, String cloudFolderName, String containerName) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.UPLOAD_FILE);
        try {
            String objectKey = cloudFolderName + "/" + file.getName();
            String url = storageService.upload(containerName, file.getAbsolutePath(),
                    objectKey, Option.apply(false), Option.apply(1), Option.apply(5), Option.empty());
            Map<String, String> uploadedFile = new HashMap<>();
            uploadedFile.put(Constants.NAME, file.getName());
            uploadedFile.put(Constants.URL, url);
            response.getResult().putAll(uploadedFile);
            return response;
        } catch (Exception e) {
            log.error("Failed to upload file. Exception: ", e);
            response.getParams().setStatus(Constants.FAILED);
            response.getParams().setErrMsg("Failed to upload file. Exception: " + e.getMessage());
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }
    }

    private void updateMetricsDbOperation(String apiName, String dbType, String operationType, long time) {
        if (ApiMetricsTracker.isTrackingEnabled()) {
            ApiMetricsTracker.recordDbOperation(apiName, dbType, operationType, System.currentTimeMillis() - time);
        }
    }

    private void updateMetricsApiCall(String apiName) {
        if (ApiMetricsTracker.isTrackingEnabled()) {
            ApiMetricsTracker.recordApiCall(apiName);
        }
    }

    private boolean validateCommunityId(String communityId) {
        Optional<CommunityEntity> communityEntityOptional = communityEngagementRepository.findByCommunityIdAndIsActive(communityId, true);
        if (communityEntityOptional.isPresent()) {
            return true;
        }
        return false;
    }
    @Override
    public ApiResponse updateAnswerPost(JsonNode answerPostData, String token) {
        log.info("DiscussionService::updateAnswerPost:updating answerPost");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.updateAnswerPost");
        payloadValidation.validatePayload(Constants.ANSWER_POST_UPDATE_VALIDATION_SCHEMA, answerPostData);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || userId.equals(Constants.UNAUTHORIZED)) {
            response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        updateMetricsApiCall(Constants.DISCUSSION_ANSWER_POST);
        long redisTimer = System.currentTimeMillis();
        DiscussionEntity discussionEntity = discussionRepository.findById(answerPostData.get(Constants.ANSWER_POST_ID).asText()).orElse(null);
        updateMetricsDbOperation(Constants.DISCUSSION_ANSWER_POST, Constants.POSTGRES, Constants.READ, redisTimer);
        if (discussionEntity == null || !discussionEntity.getIsActive()) {
            return returnErrorMsg(Constants.INVALID_DISCUSSION_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        ObjectNode data = (ObjectNode) discussionEntity.getData();
        String type = data.get(Constants.TYPE).asText();
        if (!type.equals(Constants.ANSWER_POST)) {
            return returnErrorMsg(Constants.INVALID_ANSWER_POST_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        if (data.get(Constants.STATUS).asText().equals(Constants.SUSPENDED)) {
            return returnErrorMsg(Constants.DISCUSSION_SUSPENDED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }

        try {
            ObjectNode answerPostDataNode = (ObjectNode) answerPostData;
            answerPostDataNode.remove(Constants.ANSWER_POST_ID);
            //answerPostDataNode.put("updatedBy", userId);
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            answerPostDataNode.put(Constants.UPDATED_ON, getFormattedCurrentTime(currentTime));
            discussionEntity.setUpdatedOn(currentTime);
            data.setAll(answerPostDataNode);
            discussionEntity.setData(data);
            long timer = System.currentTimeMillis();
            discussionRepository.save(discussionEntity);
            updateMetricsDbOperation(Constants.DISCUSSION_ANSWER_POST, Constants.POSTGRES, Constants.UPDATE, timer);

            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll(data);
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.updateDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionEntity.getDiscussionId(), map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + String.valueOf(discussionEntity.getDiscussionId()), jsonNode);
            redisTemplate.opsForValue()
                .getAndDelete(generateRedisJwtTokenKey(createSearchCriteriaWithDefaults(
                    data.get(Constants.PARENT_DISCUSSION_ID).asText(),
                    data.get(Constants.COMMUNITY_ID).asText(),
                    Constants.ANSWER_POST)));
            log.info("AnswerPost updated successfully");
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResult(map);
        } catch (Exception e) {
            log.error("Failed to update AnswerPost: {}", e.getMessage(), e);
            createErrorResponse(response, Constants.FAILED_TO_UPDATE_ANSWER_POST, HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse bookmarkDiscussion(String token, String communityId, String discussionId) {
        log.info("DiscussionService::bookmarkDiscussion: Bookmarking discussion");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.bookmarkDiscussion");
        if (StringUtils.isBlank(discussionId)) {
            return returnErrorMsg(Constants.INVALID_DISCUSSION_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }

        if (StringUtils.isBlank(communityId)) {
            return returnErrorMsg(Constants.INVALID_COMMUNITY_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || Constants.UNAUTHORIZED.equals(userId)) {
            return returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
        }

        try {
            Optional<DiscussionEntity> discussionDbData = discussionRepository.findById(discussionId);
            if (!discussionDbData.isPresent()) {
                return returnErrorMsg(Constants.DISCUSSION_NOT_FOUND, HttpStatus.NOT_FOUND, response, Constants.FAILED);
            }
            DiscussionEntity discussionEntity = discussionDbData.get();
            if (!discussionEntity.getIsActive()) {
                return returnErrorMsg(Constants.DISCUSSION_IS_INACTIVE, HttpStatus.CONFLICT, response, Constants.FAILED);
            }

            String bookMarkedCommunityId = discussionEntity.getData().get(Constants.COMMUNITY_ID).asText();
            if (!bookMarkedCommunityId.equals(communityId)) {
                return returnErrorMsg(Constants.INVALID_COMMUNITY_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            }

            Map<String, Object> properties = new HashMap<>();
            properties.put(Constants.USERID, userId);
            properties.put(Constants.COMMUNITY_ID, communityId);
            properties.put(Constants.DISCUSSION_ID, discussionId);

            // Check if the bookmark already exists
            List<Map<String, Object>> existingBookmarks = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                    Constants.KEYSPACE_SUNBIRD, Constants.DISCUSSION_BOOKMARKS, properties, Arrays.asList(Constants.STATUS), null);

            if (!existingBookmarks.isEmpty() && (boolean)existingBookmarks.get(0).get(Constants.STATUS)) {
                return returnErrorMsg(Constants.ALREADY_BOOKMARKED, HttpStatus.OK, response, Constants.FAILED);
            }

            // Insert the new bookmark
            properties.put(Constants.CREATED_ON, new Timestamp(System.currentTimeMillis()));
            properties.put(Constants.STATUS, true);
            cassandraOperation.insertRecord(Constants.KEYSPACE_SUNBIRD, Constants.DISCUSSION_BOOKMARKS, properties);
            cacheService.deleteCache(Constants.DISCUSSION_CACHE_PREFIX + Constants.COMMUNITY + communityId + userId);
            Map<String, Object> map = new HashMap<>();
            map.put(Constants.CREATED_ON, properties.get(Constants.CREATED_ON));
            map.put(Constants.COMMUNITY_ID, communityId);
            map.put(Constants.DISCUSSION_ID, discussionId);
            response.setResult(map);
            return response;
        } catch (Exception e) {
            log.error("DiscussionService::bookmarkDiscussion: Failed to bookmark discussion", e);
            return returnErrorMsg(Constants.DISCUSSION_BOOKMARK_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
        }
    }

    public ApiResponse unBookmarkDiscussion(String communityId, String discussionId, String token) {
        log.info("DiscussionService::unBookmarkDiscussion: UnBookmarking discussion");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.unBookmarkDiscussion");
        if (StringUtils.isBlank(discussionId)) {
            return returnErrorMsg(Constants.INVALID_DISCUSSION_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }

        if (StringUtils.isBlank(communityId)) {
            return returnErrorMsg(Constants.INVALID_COMMUNITY_ID, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || Constants.UNAUTHORIZED.equals(userId)) {
            return returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
        }

        Map<String, Object> compositeKeys = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        compositeKeys.put(Constants.COMMUNITY_ID, communityId);
        compositeKeys.put(Constants.DISCUSSION_ID, discussionId);
        compositeKeys.put(Constants.USERID, userId);
        properties.put(Constants.STATUS, false);

        try {
            cassandraOperation.updateRecordByCompositeKey(Constants.KEYSPACE_SUNBIRD, Constants.DISCUSSION_BOOKMARKS, properties,compositeKeys);
            cacheService.deleteCache(Constants.DISCUSSION_CACHE_PREFIX + Constants.COMMUNITY + communityId + userId);
            return response;
        } catch (Exception e) {
            log.error("DiscussionService::unBookmarkDiscussion: Failed to unBookmark discussion", e);
            return returnErrorMsg(Constants.DISCUSSION_UN_BOOKMARK_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
        }
    }

    @Override
    public ApiResponse getBookmarkedDiscussions(String token, Map<String, Object> requestData) {
        log.info("DiscussionService::getBookmarkedDiscussions: Fetching bookmarked discussions");
        ApiResponse response = ProjectUtil.createDefaultResponse("discussion.getBookmarkedDiscussions");
        String errorMsg = validateGetBookmarkedDiscussions(requestData);

        if (StringUtils.isNotBlank(errorMsg)) {
            return returnErrorMsg(errorMsg, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || Constants.UNAUTHORIZED.equals(userId)) {
            return returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
        }
        try {
            List<String> cachedKeys = new ArrayList<>();
            String cachedJson = cacheService.getCache(Constants.DISCUSSION_CACHE_PREFIX + Constants.COMMUNITY + requestData.get(Constants.COMMUNITY_ID) + userId);
            if (StringUtils.isNotBlank(cachedJson)) {
                cachedKeys = objectMapper.readValue(cachedJson, new TypeReference<List<String>>() {
                });
            } else {
                Map<String, Object> properties = new HashMap<>();
                properties.put(Constants.USERID, userId);
                properties.put(Constants.COMMUNITY_ID, requestData.get(Constants.COMMUNITY_ID));
                List<Map<String, Object>> bookmarkedDiscussions = cassandraOperation.getRecordsByPropertiesWithoutFiltering(Constants.KEYSPACE_SUNBIRD, Constants.DISCUSSION_BOOKMARKS, properties, Arrays.asList(Constants.DISCUSSION_ID, Constants.STATUS), null);
                if (bookmarkedDiscussions.isEmpty()) {
                    return returnErrorMsg(Constants.NO_DISCUSSIONS_FOUND, HttpStatus.OK, response, Constants.SUCCESS);
                }
                for (Map<String, Object> bookmarkedDiscussion : bookmarkedDiscussions) {
                    if (Boolean.TRUE.equals(bookmarkedDiscussion.get(Constants.STATUS))) {
                        cachedKeys.add((String) bookmarkedDiscussion.get(Constants.DISCUSSION_ID_KEY));
                    }
                }
                if (cachedKeys.isEmpty()) {
                    return returnErrorMsg(Constants.NO_DISCUSSIONS_FOUND, HttpStatus.OK, response, Constants.SUCCESS);
                }
                cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + Constants.COMMUNITY + requestData.get(Constants.COMMUNITY_ID) + userId, cachedKeys);
            }

            SearchCriteria searchCriteria = new SearchCriteria();
            HashMap<String, Object> filterCriteria = new HashMap<>();
            filterCriteria.put(Constants.DISCUSSION_ID, cachedKeys);
            searchCriteria.setFilterCriteriaMap(filterCriteria);
            searchCriteria.setPageNumber((int) requestData.get(Constants.PAGE));
            searchCriteria.setPageSize((int) requestData.get(Constants.PAGE_SIZE));
            searchCriteria.setOrderDirection(Constants.DESC);
            searchCriteria.setOrderBy(Constants.CREATED_ON);

            if (requestData.containsKey(Constants.SEARCH_STRING) && StringUtils.isNotBlank((String) requestData.get(Constants.SEARCH_STRING))) {
                if (((String) requestData.get(Constants.SEARCH_STRING)).length() < 3) {
                    createErrorResponse(response, Constants.MINIMUM_CHARACTERS_NEEDED, HttpStatus.BAD_REQUEST, Constants.FAILED_CONST);
                    return response;
                }
                searchCriteria.setSearchString((String) requestData.get(Constants.SEARCH_STRING));
            }

            SearchResult searchResult = redisTemplate.opsForValue().get(generateRedisJwtTokenKey(searchCriteria));
            if (searchResult == null) {
                searchResult = esUtilService.searchDocuments(cbServerProperties.getDiscussionEntity(), searchCriteria, cbServerProperties.getElasticDiscussionJsonPath());
                List<Map<String, Object>> data = searchResult.getData();
                fetchAndEnhanceDiscussions(data,false);
                searchResult.setData(data);
                redisTemplate.opsForValue().set(generateRedisJwtTokenKey(searchCriteria), searchResult, cbServerProperties.getSearchResultRedisTtl(), TimeUnit.SECONDS);
            }

            HashMap<String, Object> result = new HashMap<>();
            result.put(Constants.SEARCH_RESULTS, searchResult);
            response.setResult(result);
            return response;
        } catch (Exception e) {
            log.error("DiscussionService::getBookmarkedDiscussions: Failed to fetch bookmarked discussions", e);
            return returnErrorMsg(Constants.DISCUSSION_BOOKMARK_FETCH_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
        }
    }

    private String validateGetBookmarkedDiscussions(Map<String, Object> requestData) {
        if (requestData == null) {
            return Constants.MISSING_REQUEST_DATA;
        }

        StringBuffer errorMsg = new StringBuffer();
        List<String> errList = new ArrayList<>();

        if (!requestData.containsKey(Constants.COMMUNITY_ID) && StringUtils.isBlank((String) requestData.get(Constants.COMMUNITY_ID))) {
            errList.add(Constants.COMMUNITY_ID);
        }
        if (!requestData.containsKey(Constants.PAGE) || !(requestData.get(Constants.PAGE) instanceof Integer)) {
            errList.add(Constants.PAGE);
        }
        if (!requestData.containsKey(Constants.PAGE_SIZE) || !(requestData.get(Constants.PAGE_SIZE) instanceof Integer)) {
            errList.add(Constants.PAGE_SIZE);
        }
        if (!errList.isEmpty()) {
            errorMsg.append("Failed Due To Missing Params - ").append(errList).append(".");
        }
        return errorMsg.toString();
    }

    private void fetchAndEnhanceDiscussions(List<Map<String, Object>> discussions, boolean isAnswerPost) {
        Map<String, String> discussionToCreatedByMap = discussions.stream()
                .collect(Collectors.toMap(
                        discussion -> discussion.get(Constants.DISCUSSION_ID).toString(),
                        discussion -> discussion.get(Constants.CREATED_BY).toString()));

        Map<String, List<String>> discussionToUserTagMap = new HashMap<>();
        if (isAnswerPost) {
            discussionToUserTagMap.putAll(discussions.stream()
                    .filter(discussion -> discussion.containsKey(Constants.TAGGED_USER))
                    .collect(Collectors.toMap(
                            discussion -> discussion.get(Constants.DISCUSSION_ID).toString(),
                            discussion -> (List<String>) discussion.get(Constants.TAGGED_USER))));
        }

        Set<String> createdByIds = new HashSet<>(discussionToCreatedByMap.values());
        Set<String> userTagIds = new HashSet<>();
        if (!discussionToUserTagMap.isEmpty()) {
            userTagIds = discussionToUserTagMap.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
        }
        Set<String> allUserIds = new HashSet<>();
        allUserIds.addAll(createdByIds);
        allUserIds.addAll(userTagIds);

        long userDataRedisTime = System.currentTimeMillis();
        List<Object> redisResults = fetchDataForKeys(
                allUserIds.stream().map(id -> Constants.USER_PREFIX + id).collect(Collectors.toList())
        );
        updateMetricsDbOperation(Constants.DISCUSSION_SEARCH, Constants.REDIS, Constants.READ, userDataRedisTime);
        Map<String, Object> userDetailsMap = redisResults.stream()
                .map(user -> (Map<String, Object>) user)
                .collect(Collectors.toMap(
                        user -> user.get(Constants.USER_ID_KEY).toString(),
                        user -> user));

        List<String> missingUserIds = allUserIds.stream()
                .filter(id -> !userDetailsMap.containsKey(id))
                .collect(Collectors.toList());

        if (!missingUserIds.isEmpty()) {
            List<Object> cassandraResults = fetchUserFromPrimary(missingUserIds);
            userDetailsMap.putAll(cassandraResults.stream()
                    .map(user -> (Map<String, Object>) user)
                    .collect(Collectors.toMap(
                            user -> user.get(Constants.USER_ID_KEY).toString(),
                            user -> user)));
        }

        discussions.forEach(discussion -> {
            String discussionId = discussion.get(Constants.DISCUSSION_ID).toString();
            String createdById = discussionToCreatedByMap.get(discussionId);
            List<String> userTagIdsList = discussionToUserTagMap.get(discussionId);
            boolean hasCreatedBy = createdById != null && userDetailsMap.containsKey(createdById);
            if (hasCreatedBy) {
                discussion.put(Constants.CREATED_BY, userDetailsMap.get(createdById));
            }
            if (isAnswerPost && userTagIdsList != null && !userTagIdsList.isEmpty()) {
                List<Object> userTags = userTagIdsList.stream()
                        .filter(userDetailsMap::containsKey)
                        .map(userDetailsMap::get)
                        .collect(Collectors.toList());
                discussion.put(Constants.TAGGED_USER, userTags);
            }
        });
    }

    @Override
    public ApiResponse searchDiscussionByCommunity(Map<String, Object> searchData) {
        log.info("DiscussionServiceImpl::searchDiscussionByCommunity");
        ApiResponse response = ProjectUtil.createDefaultResponse("search.discussion.by.community");
        String error = validateCommunitySearchRequest(searchData);
        if (StringUtils.isNotEmpty(error)) {
            createErrorResponse(response, error, HttpStatus.BAD_REQUEST, Constants.FAILED_CONST);
            return response;
        }

        try {
            String cacheKey = Constants.DISCUSSION_CACHE_PREFIX + searchData.get(Constants.COMMUNITY_ID) + "_" + searchData.get(Constants.PAGE_NUMBER);
            SearchResult searchResult = redisTemplate.opsForValue().get(cacheKey);

            if (searchResult != null) {
                log.info("DiscussionServiceImpl::searchDiscussionByCommunity: search result fetched from redis");
                response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
                createSuccessResponse(response);
                return response;
            }

            SearchCriteria searchCriteria = getCriteria((int) searchData.get(Constants.PAGE_NUMBER), cbServerProperties.getDiscussionEsDefaultPageSize());
            Map<String,Object> filterCriteria = new HashMap<>();
            filterCriteria.put(Constants.COMMUNITY_ID, searchData.get(Constants.COMMUNITY_ID));
            filterCriteria.put(Constants.TYPE, Constants.QUESTION);
            filterCriteria.put(Constants.STATUS, Arrays.asList(Constants.ACTIVE, Constants.REPORTED));
            filterCriteria.put(Constants.IS_ACTIVE, true);

            searchCriteria.getFilterCriteriaMap().putAll(filterCriteria);
            searchResult = esUtilService.searchDocuments(cbServerProperties.getDiscussionEntity(), searchCriteria, cbServerProperties.getElasticDiscussionJsonPath());
            List<Map<String, Object>> discussions = searchResult.getData();

            if (searchCriteria.getRequestedFields().contains(Constants.CREATED_BY) || searchCriteria.getRequestedFields().isEmpty()) {
                fetchAndEnhanceDiscussions(discussions,false);
            }

            searchResult.setData(discussions);
            redisTemplate.opsForValue().set(cacheKey, searchResult, cbServerProperties.getDiscussionFeedRedisTtl(), TimeUnit.SECONDS);
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            createSuccessResponse(response);
            return response;
        } catch (Exception e) {
            log.error("error while searching discussion by community: {} .", e.getMessage(), e);
            createErrorResponse(response, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED_CONST);
            return response;
        }
    }

    private static Map<String, Object> getMustNotMap(String userId) {
        Map<String, Object> mustNotMap = new HashMap<>();
        List<Map<String, Object>> mustNotList = new ArrayList<>();
        Map<String, Object> reportedByMatch = new HashMap<>();
        reportedByMatch.put(Constants.REPORTED_BY, userId);
        Map<String, Object> reportedByCondition = new HashMap<>();
        reportedByCondition.put(Constants.MATCH, reportedByMatch);
        mustNotList.add(reportedByCondition);
        mustNotMap.put(Constants.MUST_NOT, mustNotList);
        return mustNotMap;
    }

    private SearchCriteria getCriteria(int pageNumber, int pageSize) {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(pageNumber);
        searchCriteria.setPageSize(pageSize);
        searchCriteria.setOrderBy(Constants.CREATED_ON);
        searchCriteria.setOrderDirection(Constants.DESC);
        searchCriteria.setFilterCriteriaMap(new HashMap<>());
        searchCriteria.setRequestedFields(new ArrayList<>());
        return searchCriteria;
    }

    private String validateCommunitySearchRequest(Map<String, Object> searchData) {

        if (searchData == null) {
            return Constants.MISSING_REQUEST_DATA;
        }

        StringBuffer errorMsg = new StringBuffer();
        List<String> errList = new ArrayList<>();

        if (!searchData.containsKey(Constants.COMMUNITY_ID) && StringUtils.isBlank((String) searchData.get(Constants.COMMUNITY_ID))) {
            errList.add(Constants.COMMUNITY_ID);
        }
        if (!searchData.containsKey(Constants.PAGE_NUMBER) || !(searchData.get(Constants.PAGE_NUMBER) instanceof Integer)) {
            errList.add(Constants.PAGE_NUMBER);
        }
        if (!errList.isEmpty()) {
            errorMsg.append("Failed Due To Missing Params - ").append(errList).append(".");
        }
        return errorMsg.toString();
    }

    private void deleteCacheByCommunity(String communityId) {
        String pattern = Constants.DISCUSSION_CACHE_PREFIX + communityId + "_*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Deleted cache keys: {}", keys);
        } else {
            log.info("No cache keys found for pattern: {}", pattern);
        }
    }

    private void updateCacheForFirstFivePages(String communityId) {
        SearchCriteria searchCriteria = getCriteria(0, 5 * cbServerProperties.getDiscussionEsDefaultPageSize());
        Map<String, Object> filterCriteria = new HashMap<>();
        filterCriteria.put(Constants.COMMUNITY_ID, communityId);
        filterCriteria.put(Constants.TYPE, Constants.QUESTION);
        searchCriteria.getFilterCriteriaMap().putAll(filterCriteria);

        try {
            SearchResult searchResult = esUtilService.searchDocuments(cbServerProperties.getDiscussionEntity(), searchCriteria, cbServerProperties.getElasticDiscussionJsonPath());
            List<Map<String, Object>> discussions = searchResult.getData();

            if (searchCriteria.getRequestedFields().contains(Constants.CREATED_BY) || searchCriteria.getRequestedFields().isEmpty()) {
                fetchAndEnhanceDiscussions(discussions,false);
            }
            String cacheKeyPrefix = Constants.DISCUSSION_CACHE_PREFIX + communityId + "_";

            for (int pageNumber = 1; pageNumber <= 5; pageNumber++) {
                int fromIndex = (pageNumber - 1) * cbServerProperties.getDiscussionEsDefaultPageSize();
                if (fromIndex >= discussions.size()) {
                    break;
                }
                int toIndex = Math.min(fromIndex + cbServerProperties.getDiscussionEsDefaultPageSize(), discussions.size());
                List<Map<String, Object>> pageDiscussions = new ArrayList<>(discussions.subList(fromIndex, toIndex));

                String cacheKey = cacheKeyPrefix + (pageNumber - 1);
                SearchResult pageResult = new SearchResult();
                pageResult.setData(pageDiscussions);
                pageResult.setTotalCount(searchResult.getTotalCount());
                pageResult.setFacets(searchResult.getFacets());
                redisTemplate.opsForValue().set(cacheKey, pageResult, cbServerProperties.getDiscussionFeedRedisTtl(), TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("Error while updating cache for community {}: {}", communityId, e.getMessage(), e);
        }
    }

    private String getFormattedCurrentTime(Timestamp currentTime) {
        ZonedDateTime zonedDateTime = currentTime.toInstant().atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);
        return zonedDateTime.format(formatter);
    }
}
