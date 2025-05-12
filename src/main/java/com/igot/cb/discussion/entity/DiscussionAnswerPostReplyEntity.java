package com.igot.cb.discussion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "discussion_answer_post_reply")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Entity
public class DiscussionAnswerPostReplyEntity {

    @Id
    private String discussionId;

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private JsonNode data;

    private Boolean isActive;

    private Timestamp createdOn;

    private Timestamp updatedOn;
}
