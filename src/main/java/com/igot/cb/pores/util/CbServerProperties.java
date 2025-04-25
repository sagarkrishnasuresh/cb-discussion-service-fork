package com.igot.cb.pores.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class CbServerProperties {

  @Value("${search.result.redis.ttl}")
  private long searchResultRedisTtl;

  @Value("${elastic.required.field.discussion.json.path}")
  private String elasticDiscussionJsonPath;

  @Value("${discussion.entity}")
  private String discussionEntity;

  @Value("${discussion.cloud.folder.name}")
  private String discussionCloudFolderName;

  @Value("${discussion.container.name}")
  private String discussionContainerName;

  @Value("${cloud.storage.type.name}")
  private String cloudStorageTypeName;

  @Value("${cloud.storage.key}")
  private String cloudStorageKey;

  @Value("${cloud.storage.secret}")
  private String cloudStorageSecret;

  @Value("${cloud.storage.endpoint}")
  private String cloudStorageEndpoint;

  @Value("${report.post.user.limit}")
  private int reportPostUserLimit;

  @Value("${discussion.es.defaultPageSize}")
  private int discussionEsDefaultPageSize;

  @Value("${discussion.feed.redis.ttl}")
  private long discussionFeedRedisTtl;

  @Value("${discussion.report.hide.post}")
  private boolean discussionReportHidePost;

  @Value("${filter.criteria.trending.feed}")
  private String filterCriteriaTrendingFeed;

  @Value("${elastic.required.field.community.json.path}")
  private String elasticCommunityJsonPath;

  @Value("${community.entity}")
  private String communityEntity;

  @Value("${kafka.topic.community.discusion.post.count}")
  private String communityPostCount;

  @Value("${kafka.topic.community.discusion.like.count}")
  private String communityLikeCount;

  @Value("${filter.criteria.global.feed}")
  private String filterCriteriaForGlobalFeed;

}
