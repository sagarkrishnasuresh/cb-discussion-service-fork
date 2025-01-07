package com.igot.cb.discussion.service.impl;

import com.igot.cb.discussion.entity.DiscussionEntity;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class DiscussionAsyncProcess {
    @Autowired
    private CacheService cacheService;
    @Autowired
    private EsUtilService esUtilService;
    @Autowired
    private CbServerProperties cbServerProperties;

    @Async
    public void updateElasticsearchAndRedis(DiscussionEntity saveJsonEntity) {
        try {
            // Elasticsearch update
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.setAll((ObjectNode) saveJsonEntity.getData());
            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(cbServerProperties.getDiscussionEntity(), Constants.INDEX_TYPE, saveJsonEntity.getDiscussionId(), map, cbServerProperties.getElasticDiscussionJsonPath());
            log.info("Updated Elasticsearch for discussion ID: {}", saveJsonEntity.getDiscussionId());

            // Redis update
            cacheService.putCache("discussion_" + saveJsonEntity.getDiscussionId(), jsonNode);
            log.info("Updated Redis cache for discussion ID: {}", saveJsonEntity.getDiscussionId());
        } catch (Exception e) {
            log.error("Failed to update Elasticsearch or Redis for discussion ID: {}", saveJsonEntity.getDiscussionId(), e);
        }
    }

}
