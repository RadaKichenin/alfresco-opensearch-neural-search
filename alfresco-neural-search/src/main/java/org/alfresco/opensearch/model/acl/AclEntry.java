package org.alfresco.opensearch.model.acl;

/**
 * Simple model class representing an ACL entry for indexing in OpenSearch.
 */
public class AclEntry {
    private String authority;
    private String permission;

    public AclEntry() {
    }

    public AclEntry(String authority, String permission) {
        this.authority = authority;
        this.permission = permission;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    /**
     * Creates a new builder for AclEntry.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for AclEntry.
     */
    public static class Builder {
        private String authority;
        private String permission;

        /**
         * Sets the authority for the AclEntry.
         * 
         * @param authority the authority
         * @return the builder instance
         */
        public Builder authority(String authority) {
            this.authority = authority;
            return this;
        }

        /**
         * Sets the permission for the AclEntry.
         * 
         * @param permission the permission
         * @return the builder instance
         */
        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        /**
         * Builds a new AclEntry with the configured values.
         * 
         * @return a new AclEntry instance
         */
        public AclEntry build() {
            return new AclEntry(authority, permission);
        }
    }
}