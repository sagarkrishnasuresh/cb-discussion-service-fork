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
import com.igot.cb.discussion.entity.DiscussionEntity;
import com.igot.cb.discussion.repository.DiscussionRepository;
import com.igot.cb.discussion.service.DiscussionService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.*;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
        payloadValidation.validatePayload(Constants.DISCUSSION_VALIDATION_FILE, discussionDetails);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId)|| userId.equals(Constants.UNAUTHORIZED)) {
            response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            ((ObjectNode) discussionDetails).put(Constants.CREATED_BY, userId);
            ((ObjectNode) discussionDetails).put(Constants.UP_VOTE_COUNT,0L);
            ((ObjectNode) discussionDetails).put(Constants.DOWN_VOTE_COUNT,0L);
            ((ObjectNode) discussionDetails).put(Constants.MEDIA,discussionDetails.get(Constants.MEDIA));
            ((ObjectNode) discussionDetails).put(Constants.STATUS,Constants.ACTIVE);

            DiscussionEntity jsonNodeEntity = new DiscussionEntity();
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            UUID id = UUIDs.timeBased();
            ((ObjectNode) discussionDetails).put(Constants.DISCUSSION_ID,String.valueOf(id));
            jsonNodeEntity.setDiscussionId(String.valueOf(id));
            jsonNodeEntity.setCreatedOn(currentTime);
            ((ObjectNode) discussionDetails).put(Constants.CREATED_ON,currentTime.toString());
            jsonNodeEntity.setIsActive(true);
            ((ObjectNode) discussionDetails).put(Constants.IS_ACTIVE,true);
            jsonNodeEntity.setData(discussionDetails);
            DiscussionEntity saveJsonEntity = discussionRepository.save(jsonNodeEntity);
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll((ObjectNode) saveJsonEntity.getData());
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, String.valueOf(id), map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache("discussion_" + String.valueOf(id), jsonNode);
            map.put(Constants.CREATED_ON,currentTime);
            response.setResponseCode(HttpStatus.CREATED);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setResult(map);
        } catch (Exception e) {
            log.error("Failed to create discussion: {}", e.getMessage(), e);
            createErrorResponse(response,Constants.FAILED_TO_CREATE_DISCUSSION,HttpStatus.INTERNAL_SERVER_ERROR,Constants.FAILED);
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
            createErrorResponse(response,Constants.ID_NOT_FOUND,HttpStatus.INTERNAL_SERVER_ERROR,Constants.FAILED);
            return response;
        }
        try {
            String cachedJson = cacheService.getCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("discussion Record coming from redis cache");
                response.setMessage(Constants.SUCCESS);
                response.setResponseCode(HttpStatus.OK);
                response.setResult((Map<String, Object>) objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                }));
            } else {
                Optional<DiscussionEntity> entityOptional = discussionRepository.findById(discussionId);
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
                    createErrorResponse(response,Constants.INVALID_ID,HttpStatus.NOT_FOUND,Constants.FAILED);
                    return response;
                }
            }
        } catch (Exception e) {
            log.error(" JSON for discussionId {}: {}", discussionId, e.getMessage(), e);
            createErrorResponse(response,"Failed to read the discussion",HttpStatus.INTERNAL_SERVER_ERROR,Constants.FAILED);
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
        ApiResponse response = ProjectUtil.createDefaultResponse("upate.Discussion");
        try {
            payloadValidation.validatePayload(Constants.DISCUSSION_UPDATE_VALIDATION_FILE, updateData);
            String discussionId = updateData.get(Constants.DISCUSSION_ID).asText();
            Optional<DiscussionEntity> discussionEntity = discussionRepository.findById(discussionId);
            if (!discussionEntity.isPresent()) {
                createErrorResponse(response, "Discussion not found", HttpStatus.NOT_FOUND, Constants.FAILED);
                return response;
            }
            DiscussionEntity discussionDbData = discussionEntity.get();
            if (!discussionDbData.getIsActive()) {
                createErrorResponse(response, Constants.DISCUSSION_IS_NOT_ACTIVE, HttpStatus.BAD_REQUEST, Constants.FAILED);
                return response;
            }
            JsonNode data = discussionDbData.getData();
            List<String> updateFields = Arrays.asList(Constants.TYPE, Constants.TITLE, Constants.DESCRIPTION_PAYLOAD, Constants.TARGET_TOPIC, Constants.TAGS);
            for (String field : updateFields) {
                if (updateData.has(field)) {
                    ((ObjectNode) data).put(field, updateData.get(field).asText());
                }
            }
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            ((ObjectNode) data).put(Constants.UPDATED_ON,String.valueOf(currentTime));
            discussionDbData.setUpdatedOn(currentTime);
            discussionDbData.setData(data);
            discussionRepository.save(discussionDbData);
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.set(Constants.DISCUSSION_ID, new TextNode(discussionDbData.getDiscussionId()));
            jsonNode.setAll((ObjectNode) data);

            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionDbData.getDiscussionId(), map, cbServerProperties.getElasticDiscussionJsonPath());
            Map<String, Object> responseMap = objectMapper.convertValue(discussionDbData, new TypeReference<Map<String, Object>>() {
            });
            response.setResponseCode(HttpStatus.OK);
            response.setResult(responseMap);
            response.getParams().setStatus(Constants.SUCCESS);
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
        ApiResponse response = ProjectUtil.createDefaultResponse("search.discussion");
        SearchResult searchResult =  redisTemplate.opsForValue().get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null) {
            log.info("DiscussionServiceImpl::searchDiscussion:  search result fetched from redis");
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            createSuccessResponse(response);
            return response;
        }
        String searchString = searchCriteria.getSearchString();
        if (searchString != null && searchString.length() < 2) {
            createErrorResponse(response, Constants.MINIMUM_CHARACTERS_NEEDED, HttpStatus.BAD_REQUEST, Constants.FAILED_CONST);
            return response;
        }
        try {
            searchResult = esUtilService.searchDocuments(cbServerProperties.getDiscussionEntity(), searchCriteria);
            List<Map<String, Object>> discussions = objectMapper.convertValue(
                    searchResult.getData(),
                    new TypeReference<List<Map<String, Object>>>() {
                    }
            );

            Map<String, String> discussionToCreatedByMap = discussions.stream()
                    .collect(Collectors.toMap(
                            discussion -> discussion.get(Constants.DISCUSSION_ID).toString(),
                            discussion -> discussion.get(Constants.CREATED_BY).toString()));

            Set<String> createdByIds = new HashSet<>(discussionToCreatedByMap.values());

            List<Object> redisResults = fetchDataForKeys(
                    createdByIds.stream().map(id -> Constants.USER_PREFIX + id).collect(Collectors.toList())
            );
            Map<String, Object> userDetailsMap = redisResults.stream()
                    .map(user -> (Map<String, Object>) user)
                    .collect(Collectors.toMap(
                            user -> user.get(Constants.USER_ID_KEY).toString(),
                            user -> user));

            List<String> missingUserIds = createdByIds.stream()
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

            List<Map<String, Object>> filteredDiscussions = new ArrayList<>();
            for (Map<String, Object> discussion : discussions) {
                String discussionId = discussion.get(Constants.DISCUSSION_ID).toString();
                String createdById = discussionToCreatedByMap.get(discussionId);
                if (createdById != null && userDetailsMap.containsKey(createdById)) {
                    discussion.put(Constants.CREATED_BY, userDetailsMap.get(createdById));
                    filteredDiscussions.add(discussion);
                }
            }

            JsonNode enhancedData = objectMapper.valueToTree(filteredDiscussions);
            searchResult.setData(enhancedData);
            redisTemplate.opsForValue().set(generateRedisJwtTokenKey(searchCriteria), searchResult, cbServerProperties.getSearchResultRedisTtl(), TimeUnit.SECONDS);
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
            createSuccessResponse(response);
            return response;
        } catch (Exception e) {
            createErrorResponse(response, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED_CONST);
            redisTemplate.opsForValue().set(generateRedisJwtTokenKey(searchCriteria), searchResult, cbServerProperties.getSearchResultRedisTtl(), TimeUnit.SECONDS);
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
        List<Map<String, Object>> userInfoList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.USER_TABLE, propertyMap,
                Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID), null);

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
                                    new TypeReference<HashMap<String, Object>>() {});

                            // Check for profile image and add to userMap if available
                            if (MapUtils.isNotEmpty(profileDetailsMap)) {
                                if (profileDetailsMap.containsKey(Constants.PROFILE_IMG) && StringUtils.isNotBlank((String) profileDetailsMap.get(Constants.PROFILE_IMG))){
                                    userMap.put(Constants.PROFILE_IMG_KEY, (String) profileDetailsMap.get(Constants.PROFILE_IMG));
                                }
                                if (profileDetailsMap.containsKey(Constants.DESIGNATION_KEY) && StringUtils.isNotEmpty((String) profileDetailsMap.get(Constants.DESIGNATION_KEY))) {

                                    userMap.put(Constants.DESIGNATION_KEY, (String) profileDetailsMap.get(Constants.PROFILE_IMG));
                                }
                                if(profileDetailsMap.containsKey(Constants.EMPLOYMENT_DETAILS) && MapUtils.isNotEmpty(
                                        (Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)) && ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).containsKey(Constants.DEPARTMENT_KEY) && StringUtils.isNotBlank(
                                        (String) ((Map<?, ?>) profileDetailsMap.get(Constants.EMPLOYMENT_DETAILS)).get(Constants.DEPARTMENT_KEY))){
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
        payloadValidation.validatePayload(Constants.DISCUSSION_ANSWER_POST_VALIDATION_FILE, answerPostData);
        if (!validateDiscussionId(answerPostData.get(Constants.PARENT_DISCUSSION_ID).asText())) {
            response.getParams().setErrMsg(Constants.INVALID_PARENT_DISCUSSION_ID);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        String userId = accessTokenValidator.verifyUserToken(token);
        if (StringUtils.isBlank(userId) || userId.equals(Constants.UNAUTHORIZED)) {
            response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            ((ObjectNode) answerPostData).put(Constants.CREATED_BY, userId);
            ((ObjectNode) answerPostData).put(Constants.VOTE_COUNT, 0);
            ((ObjectNode) answerPostData).put(Constants.MEDIA, answerPostData.get(Constants.MEDIA));
            ((ObjectNode) answerPostData).put(Constants.PARENT_DISCUSSION_ID, answerPostData.get(Constants.PARENT_DISCUSSION_ID));
            DiscussionEntity jsonNodeEntity = new DiscussionEntity();
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            UUID id = UUIDs.timeBased();
            ((ObjectNode) answerPostData).put(Constants.DISCUSSION_ID, String.valueOf(id));
            jsonNodeEntity.setDiscussionId(String.valueOf(id));
            jsonNodeEntity.setCreatedOn(currentTime);
            ((ObjectNode) answerPostData).put(Constants.CREATED_ON, currentTime.toString());
            jsonNodeEntity.setIsActive(true);
            ((ObjectNode) answerPostData).put(Constants.IS_ACTIVE, true);
            jsonNodeEntity.setData(answerPostData);
            DiscussionEntity saveJsonEntity = discussionRepository.save(jsonNodeEntity);
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll((ObjectNode) saveJsonEntity.getData());
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, String.valueOf(id), map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + String.valueOf(id), jsonNode);
            updateAnswerPostToDiscussion(answerPostData.get(Constants.PARENT_DISCUSSION_ID).asText(), String.valueOf(id));
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

    private boolean validateDiscussionId(String discussionId) {
        DiscussionEntity discussionEntity = discussionRepository.findById(discussionId).orElse(null);
        if (discussionEntity == null || !discussionEntity.getIsActive()) {
            return false;
        }
        JsonNode data = discussionEntity.getData();
        String type = data.get(Constants.TYPE).asText();
        if (type.equals(Constants.ANSWER_POST)) {
            return false;
        }
        return true;
    }

    private void updateAnswerPostToDiscussion(String parentDiscussionId, String discussionId) {
        DiscussionEntity discussionEntity = discussionRepository.findById(parentDiscussionId).get();
        JsonNode data = discussionEntity.getData();
        if (data.has(Constants.ANSWER_POSTS)) {
            Set<String> answerPostSet = new HashSet<>();
            ArrayNode existingAnswerPosts = (ArrayNode) data.get(Constants.ANSWER_POSTS);
            existingAnswerPosts.forEach(post -> answerPostSet.add(post.asText()));
            answerPostSet.add(discussionId);
            ArrayNode arrayNode = objectMapper.valueToTree(answerPostSet);
            ((ObjectNode) data).put(Constants.ANSWER_POSTS, arrayNode);
            ((ObjectNode) data).put(Constants.ANSWER_POST_COUNT, answerPostSet.size());
        } else {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            arrayNode.add(discussionId);
            ((ObjectNode) data).put(Constants.ANSWER_POSTS, arrayNode);
            ((ObjectNode) data).put(Constants.ANSWER_POST_COUNT, 1);
        }
        discussionEntity.setData(data);
        DiscussionEntity saveJsonEntity = discussionRepository.save(discussionEntity);
        log.info("DiscussionService::updateAnswerPostToDiscussion: Discussion entity updated successfully");
        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.setAll((ObjectNode) saveJsonEntity.getData());
        Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
        esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, parentDiscussionId, map, cbServerProperties.getElasticDiscussionJsonPath());
        cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + parentDiscussionId, jsonNode);
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

            JsonNode data = discussionEntity.getData();
            String currentStatus = data.has(Constants.STATUS) ? data.get(Constants.STATUS).asText() : null;

            if (Constants.SUSPENDED.equals(currentStatus)) {
                return returnErrorMsg(Constants.DISCUSSION_SUSPENDED, HttpStatus.ALREADY_REPORTED, response, Constants.FAILED);
            }

            ((ObjectNode) data).put(Constants.STATUS, Constants.SUSPENDED);
            ArrayNode reportedByNode = data.has(Constants.REPORTED_BY) ? (ArrayNode) data.get(Constants.REPORTED_BY) : objectMapper.createArrayNode();
            reportedByNode.add(userId);
            ((ObjectNode) data).put(Constants.REPORTED_REASON, objectMapper.valueToTree(reportData.get(Constants.REPORTED_REASON)));
            ((ObjectNode) data).put(Constants.REPORTED_BY, reportedByNode);

            discussionEntity.setData(data);
            discussionRepository.save(discussionEntity);
            log.info("DiscussionService::report: Discussion entity updated successfully");

            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll((ObjectNode) discussionEntity.getData());
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, discussionId, map, cbServerProperties.getElasticDiscussionJsonPath());
            cacheService.putCache(Constants.DISCUSSION_CACHE_PREFIX + discussionId, jsonNode);
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
                } else if (reportedReasonList.contains("Others")) {
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
    public ApiResponse uploadFile(MultipartFile mFile) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.DISCUSSION_UPLOAD_FILE);
        if(mFile.isEmpty()){
            return returnErrorMsg(Constants.DISCUSSION_FILE_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
        }

        File file = null;
        try {
            file = new File(System.currentTimeMillis() + "_" + mFile.getOriginalFilename());

            file.createNewFile();
            // Use try-with-resources to ensure FileOutputStream is closed
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(mFile.getBytes());
            }
            String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String uploadFolderPath = cbServerProperties.getDiscussionCloudFolderName() + "/" + yearMonth;
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
}
