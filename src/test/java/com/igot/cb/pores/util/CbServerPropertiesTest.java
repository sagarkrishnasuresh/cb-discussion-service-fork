package com.igot.cb.pores.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = CbServerProperties.class)
@TestPropertySource(properties = {
        "search.result.redis.ttl=3600",
        "elastic.required.field.discussion.json.path=/path/to/discussion.json",
        "discussion.entity=discussionEntity",
        "discussion.cloud.folder.name=discussionFolder",
        "discussion.container.name=discussionContainer",
        "cloud.storage.type.name=s3",
        "cloud.storage.key=cloudKey",
        "cloud.storage.secret=cloudSecret",
        "cloud.storage.endpoint=http://localhost:9000",
        "report.post.user.limit=5",
        "discussion.es.defaultPageSize=10",
        "discussion.feed.redis.ttl=1800",
        "discussion.report.hide.post=true",
        "filter.criteria.trending.feed=trending",
        "elastic.required.field.community.json.path=/path/to/community.json",
        "community.entity=communityEntity",
        "kafka.topic.community.discusion.post.count=topicPostCount",
        "kafka.topic.community.discusion.like.count=topicLikeCount",
        "filter.criteria.global.feed=globalFeed",
        "filter.criteria.mdo.all.report.feed=allReportFeed",
        "filter.criteria.mdo.report.question.feed=questionReportFeed",
        "filter.criteria.mdo.report.answerPost.feed=answerPostReportFeed",
        "filter.criteria.mdo.report.answerPostReply.feed=answerPostReplyReportFeed",
        "filter.criteria.mdo.all.suspended.feed=allSuspendedFeed",
        "filter.criteria.question.document.feed=questionDocFeed",
        "filter.criteria.question.user.feed=questionUserFeed",
        "user.feed.filter.criteriaMapSize=20"
})
class CbServerPropertiesTest {

    @Autowired
    private CbServerProperties properties;

    @Test
    void testAllPropertiesInjected() {
        assertEquals(3600, properties.getSearchResultRedisTtl());
        assertEquals("/path/to/discussion.json", properties.getElasticDiscussionJsonPath());
        assertEquals("discussionEntity", properties.getDiscussionEntity());
        assertEquals("discussionFolder", properties.getDiscussionCloudFolderName());
        assertEquals("discussionContainer", properties.getDiscussionContainerName());
        assertEquals("s3", properties.getCloudStorageTypeName());
        assertEquals("cloudKey", properties.getCloudStorageKey());
        assertEquals("cloudSecret", properties.getCloudStorageSecret());
        assertEquals("http://localhost:9000", properties.getCloudStorageEndpoint());
        assertEquals(5, properties.getReportPostUserLimit());
        assertEquals(10, properties.getDiscussionEsDefaultPageSize());
        assertEquals(1800, properties.getDiscussionFeedRedisTtl());
        assertTrue(properties.isDiscussionReportHidePost());
        assertEquals("trending", properties.getFilterCriteriaTrendingFeed());

    }
    @Test
    void testCommunityPropertiesInjected() {
        assertEquals("/path/to/community.json", properties.getElasticCommunityJsonPath());
        assertEquals("communityEntity", properties.getCommunityEntity());
        assertEquals("topicPostCount", properties.getCommunityPostCount());
        assertEquals("topicLikeCount", properties.getCommunityLikeCount());
        assertEquals("globalFeed", properties.getFilterCriteriaForGlobalFeed());
        assertEquals("allReportFeed", properties.getMdoAllReportFeed());
        assertEquals("questionReportFeed", properties.getMdoQuestionReportFeed());
        assertEquals("answerPostReportFeed", properties.getMdoAnswerPostReportFeed());
        assertEquals("answerPostReplyReportFeed", properties.getMdoAnswerPostReplyReportFeed());
        assertEquals("allSuspendedFeed", properties.getMdoAllSuspendedFeed());
        assertEquals("questionDocFeed", properties.getFilterCriteriaQuestionDocumentFeed());
        assertEquals("questionUserFeed", properties.getFilterCriteriaQuestionUserFeed());
        assertEquals(20, properties.getUserFeedFilterCriteriaMapSize());
    }

    @Test
    void testGettersAndSetters() {
        CbServerProperties props = new CbServerProperties();

        props.setKafkaUserPostCount("user-post-topic");
        props.setCbServiceRegistryBaseUrl("http://localhost:8080");
        props.setCbRegistryTextModerationApiPath("/text/moderation");
        props.setCbDiscussionApiKey("dummy-key");
        props.setContentModerationLanguageDetectApiPath("/lang/detect");
        props.setKafkaProcessDetectLanguageTopic("detect-language-topic");
        props.setKafkaGroupProcessDetectLanguageGroup("detect-language-group");
        props.setContentModerationServiceUrl("http://content-service");

        assertEquals("user-post-topic", props.getKafkaUserPostCount());
        assertEquals("http://localhost:8080", props.getCbServiceRegistryBaseUrl());
        assertEquals("/text/moderation", props.getCbRegistryTextModerationApiPath());
        assertEquals("dummy-key", props.getCbDiscussionApiKey());
        assertEquals("/lang/detect", props.getContentModerationLanguageDetectApiPath());
        assertEquals("detect-language-topic", props.getKafkaProcessDetectLanguageTopic());
        assertEquals("detect-language-group", props.getKafkaGroupProcessDetectLanguageGroup());
        assertEquals("http://content-service", props.getContentModerationServiceUrl());
    }
}
