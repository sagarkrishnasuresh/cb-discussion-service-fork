package com.igot.cb.discussion.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "discussion_answer_post_reply")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class DiscussionAnswerPostReplyEntity {

    @Id
    private String discussionId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode data;

    private Boolean isActive;

    private Timestamp createdOn;

    private Timestamp updatedOn;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode profanityresponse;

    @Column(name = "isprofane")
    private Boolean isProfane;

    @Column(name = "profanitycheckstatus")
    private String profanityCheckStatus;
}
