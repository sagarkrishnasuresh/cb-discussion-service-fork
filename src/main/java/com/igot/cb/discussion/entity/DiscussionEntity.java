package com.igot.cb.discussion.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Type;
import jakarta.persistence.*;
import java.sql.Timestamp;
import com.vladmihalcea.hibernate.type.json.JsonType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "discussion")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class DiscussionEntity {

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

    public DiscussionEntity(String discussionId, JsonNode data, Boolean isActive, Timestamp createdOn, Timestamp updatedOn) {
        this.discussionId = discussionId;
        this.data = data;
        this.isActive = isActive;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
    }
}
