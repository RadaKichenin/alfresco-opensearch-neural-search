package org.alfresco.opensearch.model.acl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Model class representing the ACL status of a node in Alfresco.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AclStatus {
    private List<AccessControlEntry> entries;
    private boolean inherits;
    private String owner;

    public List<AccessControlEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<AccessControlEntry> entries) {
        this.entries = entries;
    }

    public boolean isInherits() {
        return inherits;
    }

    public void setInherits(boolean inherits) {
        this.inherits = inherits;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * Model class representing an access control entry.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccessControlEntry {
        private boolean allowPermissions;
        private String authorityId;
        private String name;
        private List<String> permissions;

        public boolean isAllowPermissions() {
            return allowPermissions;
        }

        public void setAllowPermissions(boolean allowPermissions) {
            this.allowPermissions = allowPermissions;
        }

        public String getAuthorityId() {
            return authorityId;
        }

        public void setAuthorityId(String authorityId) {
            this.authorityId = authorityId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(List<String> permissions) {
            this.permissions = permissions;
        }
    }
}