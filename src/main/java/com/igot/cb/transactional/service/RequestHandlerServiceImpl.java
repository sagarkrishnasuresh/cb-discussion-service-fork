package com.igot.cb.transactional.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@SuppressWarnings("unchecked")
public class RequestHandlerServiceImpl {
    private Logger log = LoggerFactory.getLogger(RequestHandlerServiceImpl.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper mapper;

    public Map<String, Object> fetchResultUsingPost(String uri, Object request, Map<String, String> headersValues) {
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        Map<String, Object> response = new HashMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            if (!CollectionUtils.isEmpty(headersValues)) {
                headersValues.forEach(headers::set);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(request, headers);
            if (log.isDebugEnabled()) {
                log.debug("{}\nURI: {}\nRequest: {}", this.getClass().getCanonicalName() + ".fetchResult", uri, mapper.writeValueAsString(request));
            }
            response = restTemplate.postForObject(uri, entity, Map.class);
            if (log.isDebugEnabled()) {
                log.debug("Response: {}", mapper.writeValueAsString(response));
            }
        } catch (HttpClientErrorException hce) {
            log.error("Error received: {}", hce.getResponseBodyAsString(), hce);
            try {
                response = mapper.readValue(hce.getResponseBodyAsString(), new TypeReference<HashMap<String, Object>>() {});
            } catch (Exception e1) {
                log.error("Failed to parse error response: {}", e1.getMessage(), e1);
            }
        } catch (JsonProcessingException e) {
            log.error("JSON processing error: {}", e.getMessage(), e);
            try {
                log.warn("Error Response: {}", mapper.writeValueAsString(response));
            } catch (Exception e1) {
                log.error("Failed to log error response: {}", e1.getMessage(), e1);
            }
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
        }
        return response != null ? response : new HashMap<>();
    }
}
