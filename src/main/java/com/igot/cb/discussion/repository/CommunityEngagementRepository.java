package com.igot.cb.discussion.repository;

import com.igot.cb.discussion.entity.CommunityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityEngagementRepository extends JpaRepository<CommunityEntity, String> {
    Optional<CommunityEntity> findByCommunityIdAndIsActive(String communityId, boolean isActive);
}
