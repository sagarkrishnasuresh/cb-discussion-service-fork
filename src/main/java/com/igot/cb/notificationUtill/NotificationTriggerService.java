package com.igot.cb.notificationUtill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.ProjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.igot.cb.pores.util.Constants.*;

@Service
@Slf4j
public class NotificationTriggerService {

    @Value("${notification.api.url}")
    private String notificationApiUrl;

    private final RestTemplate restTemplate;

    @Autowired
    public NotificationTriggerService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Autowired
    private ObjectMapper objectMapper;

    public ApiResponse sendNotification(
            String subCategory,
            String subType,
            List<String> userIds,
            Map<String, Object> message
    ) {
        ApiResponse response = ProjectUtil.createDefaultResponse("notification.send");

        try {
            if (!StringUtils.hasText(subCategory)) {
                throw new IllegalArgumentException("subCategory is required");
            }
            if (!StringUtils.hasText(subType)) {
                throw new IllegalArgumentException("subType is required");
            }

            if (CollectionUtils.isEmpty(userIds)) {
                throw new IllegalArgumentException("userIds cannot be null or empty");
            }

            if (message == null || message.isEmpty()) {
                throw new IllegalArgumentException("message cannot be null or empty");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put(SUB_CATEGORY, subCategory);
            payload.put(SUB_TYPE,subType);
            payload.put(USER_IDS, userIds);
            payload.put(MESSAGE, message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> serviceResponse = restTemplate.postForEntity(
                    notificationApiUrl, request, Map.class
            );

            response.setResponseCode(HttpStatus.OK);
            response.setResult(serviceResponse.getBody());
            return response;

        } catch (IllegalArgumentException iae) {
            log.warn("Invalid input for sendNotification: {}", iae.getMessage());
            updateErrorDetails(response, iae.getMessage(), HttpStatus.BAD_REQUEST);
            return response;

        } catch (HttpClientErrorException hce) {
            log.error("HTTP error while sending notification: {}", hce.getResponseBodyAsString(), hce);
            updateErrorDetails(response, "Client error: " + hce.getResponseBodyAsString(), (HttpStatus) hce.getStatusCode());
            return response;

        } catch (Exception e) {
            log.error("Unexpected error while sending notification: {}", e.getMessage(), e);
            updateErrorDetails(response, "Unexpected error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }


    }

    private void updateErrorDetails(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrMsg(errorMessage);
        response.setResponseCode(httpStatus);
    }


    public void triggerNotification(
            String subCategory,
            String subType,
            List<String> userIds,
            String title,
            String userName,
            Map<String, Object> data
    ) {
        ObjectNode placeholders = objectMapper.createObjectNode();
        placeholders.put(TITLE, title);
        placeholders.put(USER_NAME, userName);

        Map<String, Object> message = new HashMap<>();
        message.put(PLACE_HOLDERS, placeholders);
        message.put(DATA, data);
        log.info("notifications message in triggerNotification:{}", message);

        try {
            sendNotification(subCategory,subType, userIds, message);
            log.info("Notification sent successfully for subCategory: {}", subCategory);
        } catch (Exception e) {
            log.error("Notification failed for subCategory: {}", subCategory, e);
        }
    }
}