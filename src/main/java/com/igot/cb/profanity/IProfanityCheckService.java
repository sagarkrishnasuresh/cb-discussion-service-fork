package com.igot.cb.profanity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

/**
 * Interface for profanity check service.
 * This service is responsible for processing profanity checks on discussion details.
 */
@Service
public interface IProfanityCheckService {
    /**
     * Processes a profanity check for a discussion.
     * @param discussionId the ID of the discussion to check
     * @param discussionDetailsNode the details of the discussion as an ObjectNode
     */
    void processProfanityCheck(String discussionId, ObjectNode discussionDetailsNode);
}
