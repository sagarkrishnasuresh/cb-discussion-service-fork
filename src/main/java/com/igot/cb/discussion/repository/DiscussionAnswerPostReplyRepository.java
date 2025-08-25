package com.igot.cb.discussion.repository;

import com.igot.cb.discussion.entity.DiscussionAnswerPostReplyEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionAnswerPostReplyRepository extends JpaRepository<DiscussionAnswerPostReplyEntity, String> {
    @Modifying
    @Transactional
    @Query(value = "UPDATE discussion_answer_post_reply SET profanityresponse = cast(?2 as jsonb), isprofane = ?3, profanitycheckstatus = ?4 WHERE discussion_id = ?1", nativeQuery = true)
    void updateProfanityFieldsByDiscussionId(String discussionId, String profanityResponseJson, Boolean isProfane, String profanityCheckStatus);

    @Modifying
    @Transactional
    @Query(value = "UPDATE discussion_answer_post_reply SET profanitycheckstatus = ?2, isprofane = ?3 WHERE discussion_id = ?1", nativeQuery = true)
    void updateProfanityCheckStatusByDiscussionId(String discussionId, String profanityCheckStatus, Boolean isProfane);
}
