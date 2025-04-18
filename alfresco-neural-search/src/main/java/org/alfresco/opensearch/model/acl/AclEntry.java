package org.alfresco.opensearch.model.acl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Model class representing the ACL status of a node in Alfresco.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AclStatus {
    private List<AccessControlEntry> entries;
    private boolean inherits;
    private String owner;

    /**
     * Model class representing an access control entry.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccessControlEntry {
        private boolean allowPermissions;
        private String authorityId;
        private String name;
        private List<String> permissions;
    }
}