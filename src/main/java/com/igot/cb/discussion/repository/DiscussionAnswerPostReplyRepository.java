package com.igot.cb.discussion.repository;

import com.igot.cb.discussion.entity.DiscussionAnswerPostReplyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionAnswerPostReplyRepository extends JpaRepository<DiscussionAnswerPostReplyEntity, String> {
}
