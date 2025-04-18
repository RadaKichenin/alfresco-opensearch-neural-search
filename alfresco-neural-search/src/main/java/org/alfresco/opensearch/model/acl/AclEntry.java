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
}