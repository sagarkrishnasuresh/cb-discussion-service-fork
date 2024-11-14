package com.igot.cb.discussion.repository;

import com.igot.cb.discussion.entity.DiscussionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionRepository extends JpaRepository<DiscussionEntity, String>{
}
