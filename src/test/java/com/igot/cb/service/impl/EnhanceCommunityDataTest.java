package com.igot.cb.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.discussion.entity.CommunityEntity;
import com.igot.cb.discussion.repository.CommunityEngagementRepository;
import com.igot.cb.discussion.service.impl.DiscussionServiceImpl;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnhanceCommunityDataTest {

    @InjectMocks
    private DiscussionServiceImpl discussionService;

    @Mock
    private CommunityEngagementRepository communityEngagementRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testEnhanceCommunityData_allCommunitiesInRedis() throws Exception {
        DiscussionServiceImpl spyService = spy(discussionService);
        
        List<Map<String, Object>> discussions = new ArrayList<>();
        Map<String, Object> discussion1 = new HashMap<>();
        discussion1.put(Constants.COMMUNITY_ID, "comm1");
        Map<String, Object> discussion2 = new HashMap<>();
        discussion2.put(Constants.COMMUNITY_ID, "comm2");
        discussions.add(discussion1);
        discussions.add(discussion2);
        
        List<Object> redisResults = new ArrayList<>();
        Map<String, Object> redisCommunity1 = new HashMap<>();
        redisCommunity1.put(Constants.COMMUNITY_ID, "comm1");
        redisCommunity1.put(Constants.COMMUNITY_NAME, "Community 1");
        Map<String, Object> redisCommunity2 = new HashMap<>();
        redisCommunity2.put(Constants.COMMUNITY_ID, "comm2");
        redisCommunity2.put(Constants.COMMUNITY_NAME, "Community 2");
        redisResults.add(redisCommunity1);
        redisResults.add(redisCommunity2);
        
        doReturn(redisResults).when(spyService).fetchDataForKeys(anyList(), eq(false));
        
        Method method = DiscussionServiceImpl.class.getDeclaredMethod("enhanceCommunityData", List.class);
        method.setAccessible(true);
        method.invoke(spyService, discussions);
        
        assertEquals("Community 1", discussion1.get(Constants.COMMUNITY_NAME));
        assertEquals("Community 2", discussion2.get(Constants.COMMUNITY_NAME));
    }

    @Test
    void testEnhanceCommunityData_noCommunitiesInRedis() throws Exception {
        DiscussionServiceImpl spyService = spy(discussionService);

        List<Map<String, Object>> discussions = new ArrayList<>();
        Map<String, Object> discussion1 = new HashMap<>();
        discussion1.put(Constants.COMMUNITY_ID, "comm1");
        discussions.add(discussion1);

        List<Object> redisResults = new ArrayList<>();

        doReturn(redisResults).when(spyService).fetchDataForKeys(anyList(), eq(false));
        CommunityEntity mockEntity = mock(CommunityEntity.class);

        // Create JSON for the "data" field
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode dataNode = mapper.createObjectNode();
        dataNode.put(Constants.COMMUNITY_NAME, "Test Community");

        // Mock getCommunityId and getData
        when(mockEntity.getCommunityId()).thenReturn("c1");
        when(mockEntity.getData()).thenReturn(dataNode);

        when(communityEngagementRepository.findAllById(anyList()))
                .thenReturn(List.of(mockEntity));
        Method method = DiscussionServiceImpl.class.getDeclaredMethod("enhanceCommunityData", List.class);
        method.setAccessible(true);
        method.invoke(spyService, discussions);

        assertNotNull(discussion1.get(Constants.COMMUNITY_ID));

    }
    @Test
    void testEnhanceCommunityData_emptyDiscussionsList() throws Exception {
        DiscussionServiceImpl spyService = spy(discussionService);
        
        List<Map<String, Object>> discussions = new ArrayList<>();
        List<Object> redisResults = new ArrayList<>();
        
        doReturn(redisResults).when(spyService).fetchDataForKeys(anyList(), eq(false));
        
        Method method = DiscussionServiceImpl.class.getDeclaredMethod("enhanceCommunityData", List.class);
        method.setAccessible(true);
        assertDoesNotThrow(()-> method.invoke(spyService, discussions));
        
    }

    @Test
    void testEnhanceCommunityData_duplicateCommunityIds() throws Exception {
        DiscussionServiceImpl spyService = spy(discussionService);
        
        List<Map<String, Object>> discussions = new ArrayList<>();
        Map<String, Object> discussion1 = new HashMap<>();
        discussion1.put(Constants.COMMUNITY_ID, "comm1");
        Map<String, Object> discussion2 = new HashMap<>();
        discussion2.put(Constants.COMMUNITY_ID, "comm1");
        Map<String, Object> discussion3 = new HashMap<>();
        discussion3.put(Constants.COMMUNITY_ID, "comm2");
        discussions.add(discussion1);
        discussions.add(discussion2);
        discussions.add(discussion3);
        
        List<Object> redisResults = new ArrayList<>();
        Map<String, Object> redisCommunity1 = new HashMap<>();
        redisCommunity1.put(Constants.COMMUNITY_ID, "comm1");
        redisCommunity1.put(Constants.COMMUNITY_NAME, "Community 1");
        Map<String, Object> redisCommunity2 = new HashMap<>();
        redisCommunity2.put(Constants.COMMUNITY_ID, "comm2");
        redisCommunity2.put(Constants.COMMUNITY_NAME, "Community 2");
        redisResults.add(redisCommunity1);
        redisResults.add(redisCommunity2);
        
        doReturn(redisResults).when(spyService).fetchDataForKeys(anyList(), eq(false));
        
        Method method = DiscussionServiceImpl.class.getDeclaredMethod("enhanceCommunityData", List.class);
        method.setAccessible(true);
        method.invoke(spyService, discussions);
        
        assertEquals("Community 1", discussion1.get(Constants.COMMUNITY_NAME));
        assertEquals("Community 1", discussion2.get(Constants.COMMUNITY_NAME));
        assertEquals("Community 2", discussion3.get(Constants.COMMUNITY_NAME));
        verify(spyService).fetchDataForKeys(argThat(list -> list.size() == 2), eq(false));
    }
}