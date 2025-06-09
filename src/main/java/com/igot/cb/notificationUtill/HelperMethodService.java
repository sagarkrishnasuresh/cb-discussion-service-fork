package com.igot.cb.notificationUtill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.metrics.service.ApiMetricsTracker;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HelperMethodService {
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CassandraOperation cassandraOperation;
    @Autowired
    private CacheService cacheService;

    public List<Object> fetchDataForKeys(List<String> keys, boolean isUserData) {
        // Fetch values for all keys from Redis
        List<Object> values;
        if (isUserData) {
            values = cacheService.hget(keys);
        } else {
            values = cacheService.hgetMulti(keys);
        }

        // Create a map of key-value pairs, converting stringified JSON objects to User objects
        return keys.stream()
                .filter(key -> values.get(keys.indexOf(key)) != null) // Filter out null values
                .map(key -> {
                    String stringifiedJson = (String) values.get(keys.indexOf(key)); // Cast the value to String
                    try {
                        // Convert the stringified JSON to a User object using ObjectMapper
                        return objectMapper.readValue(stringifiedJson, Object.class); // You can map this to a specific User type if needed
                    } catch (Exception e) {
                        log.error("Failed to fetch user data from redis ", e.getMessage(), e);
                        return null; // Return null in case of error
                    }
                })
                .collect(Collectors.toList());
    }

    public List<Object> fetchUserFromPrimary(List<String> userIds) {
        log.info("DiscussionServiceImpl::fetchUserFromPrimary: Fetching user data from Cassandra");
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

    private void updateMetricsDbOperation(String apiName, String dbType, String operationType, long time) {
        if (ApiMetricsTracker.isTrackingEnabled()) {
            ApiMetricsTracker.recordDbOperation(apiName, dbType, operationType, System.currentTimeMillis() - time);
        }
    }

    public String fetchUserFirstName(String userId) {
        List<Object> redisResults = fetchDataForKeys(List.of(Constants.USER_PREFIX + userId), true);
        if (!redisResults.isEmpty() && redisResults.get(0) instanceof Map) {
            String name = (String) ((Map<?, ?>) redisResults.get(0)).get(Constants.FIRST_NAME_KEY);
            if (StringUtils.isNotBlank(name)) return name;
        }

        List<Object> cassandraResults = fetchUserFromPrimary(List.of(userId));
        if (!cassandraResults.isEmpty() && cassandraResults.get(0) instanceof Map) {
            String name = (String) ((Map<?, ?>) cassandraResults.get(0)).get(Constants.FIRST_NAME_KEY);
            if (StringUtils.isNotBlank(name)) return name;
        }

        return "User";
    }

}
